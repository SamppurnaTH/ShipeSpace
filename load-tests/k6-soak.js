/**
 * k6 soak test — sustained load for 45 minutes.
 *
 * PURPOSE:
 * Short burst tests prove correctness. Soak tests prove stability.
 * These are the bugs that only appear after 20+ minutes of sustained load:
 *   - Memory leaks (heap grows until OOM)
 *   - Connection leaks (pool exhausts, new requests queue forever)
 *   - GC pressure (p99 latency spikes every few minutes as GC runs)
 *   - Thread leaks (thread count grows, CPU spikes)
 *   - Cache TTL drift (entries expire, thundering herd every 15 min)
 *
 * WHAT TO WATCH DURING THE RUN:
 *   1. JVM heap: should be stable, not growing
 *      curl http://localhost:8080/api/actuator/metrics/jvm.memory.used
 *
 *   2. MongoDB connection pool: should stay within bounds
 *      curl http://localhost:8080/api/actuator/metrics/mongodb.driver.pool.size
 *
 *   3. Thread count: should be stable
 *      curl http://localhost:8080/api/actuator/metrics/jvm.threads.live
 *
 *   4. p99 latency: should be stable, not drifting upward over time
 *      Watch the k6 output — if p99 increases by >50% over 45 min, you have a leak.
 *
 *   5. GC pause time: spikes are normal, sustained high GC is not
 *      curl http://localhost:8080/api/actuator/metrics/jvm.gc.pause
 *
 * PASS CRITERIA:
 *   - Zero 5xx errors for the entire 45-minute run
 *   - p99 latency does not drift upward by more than 50% from baseline
 *   - Error rate < 0.1% (429s from rate limiting are excluded)
 *   - System handles the trending cache TTL expiry at minute 15 and 30
 *     without a latency spike > 2x baseline
 *
 * SETUP:
 *   docker-compose up -d
 *   export BASE_URL=http://localhost:8086
 *   export TEST_JWT="Bearer eyJ..."
 *   export TEST_SHIP_ID="<ship-id>"
 *
 *   k6 run --env BASE_URL=$BASE_URL \
 *          --env TEST_JWT=$TEST_JWT \
 *          --env TEST_SHIP_ID=$TEST_SHIP_ID \
 *          load-tests/k6-soak.js
 *
 * READING THE RESULTS:
 *   k6 outputs rolling p95/p99 every 10s. Export to InfluxDB + Grafana for
 *   time-series visualization of latency drift:
 *
 *   k6 run --out influxdb=http://localhost:8086/k6 load-tests/k6-soak.js
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend, Gauge } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const JWT = __ENV.TEST_JWT || "";
const SHIP_ID = __ENV.TEST_SHIP_ID || "test-ship-id";

// ── Metrics ───────────────────────────────────────────────────────────────────

const serverErrors    = new Counter("server_errors_5xx");
const invariantFails  = new Counter("invariant_violations");
const likeLatency     = new Trend("like_latency_ms",    true);
const feedLatency     = new Trend("feed_latency_ms",    true);
const profileLatency  = new Trend("profile_latency_ms", true);
const errorRate       = new Rate("error_rate");

// ── Thresholds ────────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    /**
     * Core engagement — sustained like/unlike at moderate load.
     * 30 VUs for 45 minutes. This is the "normal production traffic" baseline.
     */
    sustained_engagement: {
      executor: "constant-vus",
      vus: 30,
      duration: "45m",
      exec: "engagementMix",
    },

    /**
     * Feed reads — trending feed is cached for 15 minutes.
     * At minute 15 and 30, the cache expires and all readers hit MongoDB.
     * This is the thundering herd scenario that happens in production after
     * every cache TTL expiry. The system must absorb it without a latency spike.
     */
    feed_reads: {
      executor: "constant-vus",
      vus: 20,
      duration: "45m",
      exec: "feedRead",
    },

    /**
     * Profile reads — user profiles are cached for 2 minutes.
     * Steady read load to verify the profile cache doesn't leak.
     */
    profile_reads: {
      executor: "constant-vus",
      vus: 10,
      duration: "45m",
      exec: "profileRead",
    },

    /**
     * Invariant checker — reads the ship state every 30 seconds.
     * Any invariant violation during the 45-minute run is a test failure.
     * This catches counter drift that accumulates slowly over time.
     */
    invariant_checker: {
      executor: "constant-vus",
      vus: 2,
      duration: "45m",
      exec: "checkInvariants",
    },
  },

  thresholds: {
    // Zero tolerance for correctness failures
    "server_errors_5xx":   ["count==0"],
    "invariant_violations": ["count==0"],

    // Latency must be stable over 45 minutes
    // If these breach, latency is drifting — likely a leak
    "like_latency_ms":    ["p(95)<500",  "p(99)<2000"],
    "feed_latency_ms":    ["p(95)<1000", "p(99)<3000"],
    "profile_latency_ms": ["p(95)<300",  "p(99)<1000"],

    // Error rate — 429s excluded (rate limiting is expected)
    "error_rate": ["rate<0.001"],
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

const authHeaders = { Authorization: JWT, "Content-Type": "application/json" };

export function engagementMix() {
  const t = Date.now();

  if (Math.random() < 0.5) {
    const res = http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    likeLatency.add(Date.now() - t);
    trackResult(res, "like");
  } else {
    const res = http.del(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    likeLatency.add(Date.now() - t);
    trackResult(res, "unlike");
  }

  // Realistic think time — users don't hammer the API
  sleep(Math.random() * 2 + 1);
}

export function feedRead() {
  const page = Math.floor(Math.random() * 3);
  const t = Date.now();
  const res = http.get(`${BASE_URL}/api/feeds/trending?page=${page}&size=20`);
  feedLatency.add(Date.now() - t);

  const ok = check(res, {
    "feed: 200": (r) => r.status === 200,
    "feed: has content": (r) => {
      try { return JSON.parse(r.body).content !== undefined; }
      catch { return false; }
    },
  });

  if (res.status >= 500) serverErrors.add(1);
  errorRate.add(res.status >= 500 ? 1 : 0);

  sleep(Math.random() * 3 + 2); // users read content before next request
}

export function profileRead() {
  const t = Date.now();
  const res = http.get(`${BASE_URL}/api/auth/me`, { headers: authHeaders });
  profileLatency.add(Date.now() - t);

  const ok = check(res, {
    "profile: 200 or 401": (r) => r.status === 200 || r.status === 401,
    "profile: not 5xx": (r) => r.status < 500,
  });

  if (res.status >= 500) serverErrors.add(1);
  errorRate.add(res.status >= 500 ? 1 : 0);

  sleep(5); // profile reads are infrequent
}

export function checkInvariants() {
  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers: authHeaders });

  if (res.status !== 200) {
    if (res.status >= 500) {
      serverErrors.add(1);
      invariantFails.add(1);
      console.error(`Invariant check failed: ship read returned ${res.status}`);
    }
    sleep(30);
    return;
  }

  try {
    const ship = JSON.parse(res.body);

    if (ship.likeCount < 0) {
      invariantFails.add(1);
      console.error(`INVARIANT VIOLATION at ${new Date().toISOString()}: likeCount=${ship.likeCount} (negative)`);
    }

    if (ship.likeCount > 10000) {
      invariantFails.add(1);
      console.error(`INVARIANT VIOLATION at ${new Date().toISOString()}: likeCount=${ship.likeCount} (overflow)`);
    }

  } catch (e) {
    console.error(`Failed to parse ship response: ${e}`);
  }

  sleep(30); // check every 30 seconds
}

export function teardown() {
  console.log("\n=== SOAK TEST COMPLETE ===");
  console.log("Duration: 45 minutes");
  console.log("");
  console.log("Check these metrics in the k6 summary:");
  console.log("  server_errors_5xx    — must be 0");
  console.log("  invariant_violations — must be 0");
  console.log("  like_latency_ms p99  — must be < 2000ms AND stable (not drifting)");
  console.log("  feed_latency_ms p99  — must be < 3000ms AND stable");
  console.log("");
  console.log("Check these JVM metrics for leaks:");
  console.log(`  ${BASE_URL}/api/actuator/metrics/jvm.memory.used`);
  console.log(`  ${BASE_URL}/api/actuator/metrics/jvm.threads.live`);
  console.log(`  ${BASE_URL}/api/actuator/metrics/jvm.gc.pause`);
  console.log("");
  console.log("If p99 increased by >50% from start to end: you have a leak.");
  console.log("If heap grew monotonically: you have a memory leak.");
  console.log("If thread count grew: you have a thread leak.");
}
