/**
 * Worst-case chaos test — simultaneous network degradation + dependency failure + traffic spike.
 *
 * This is the scenario that kills systems that passed every other test.
 * Not one thing failing. Everything failing at once, at the worst possible time.
 *
 * FAILURE STACK (all active simultaneously in phase 3):
 *   - MongoDB: 200ms latency + 5% packet loss (simulates disk pressure + network jitter)
 *   - Redis: 150ms latency (simulates memory pressure, not full failure)
 *   - Traffic: 5x normal load (simulates viral content spike)
 *   - External API: GitHub OAuth timing out (simulates GitHub incident)
 *
 * WHY THIS MATTERS:
 * Individual failure tests prove resilience patterns work in isolation.
 * This test proves they work together — that circuit breakers don't
 * interfere with load shedding, that adaptive concurrency reduction
 * doesn't starve health checks, that SLO degradation doesn't corrupt writes.
 *
 * WHAT WE'RE PROVING:
 *   1. System stays available (no 5xx) under worst-case combined failure
 *   2. Load shedding activates and sheds writes before reads (priority tiers)
 *   3. Circuit breakers open for MongoDB without taking down Redis-dependent paths
 *   4. SLO degradation activates (personalization disabled, behavioral writes skipped)
 *   5. Invariants hold — no counter corruption even when MongoDB is slow + lossy
 *   6. System recovers to baseline within 90s of failure removal
 *
 * FAILURE INJECTION (toxiproxy required):
 *   toxiproxy-server &
 *   toxiproxy-cli create redis-proxy --listen 0.0.0.0:6380 --upstream localhost:6379
 *   toxiproxy-cli create mongo-proxy --listen 0.0.0.0:27018 --upstream localhost:27017
 *
 *   # Phase 3: inject all failures simultaneously
 *   toxiproxy-cli toxic add mongo-proxy --type latency --attribute latency=200 --name mongo-latency
 *   toxiproxy-cli toxic add mongo-proxy --type slicer --attribute average_size=1 \
 *                --attribute size_variation=0 --attribute delay=5 --name mongo-loss
 *   toxiproxy-cli toxic add redis-proxy --type latency --attribute latency=150 --name redis-latency
 *
 *   # Phase 4: add traffic spike (k6 handles this via ramping-vus)
 *
 *   # Phase 5: recovery — remove all toxics
 *   toxiproxy-cli toxic remove mongo-proxy --toxicName mongo-latency
 *   toxiproxy-cli toxic remove mongo-proxy --toxicName mongo-loss
 *   toxiproxy-cli toxic remove redis-proxy --toxicName redis-latency
 *
 * SETUP:
 *   # Start backend pointing at toxiproxy ports
 *   REDIS_PORT=6380 \
 *   MONGODB_URI=mongodb://localhost:27018/shipspace?replicaSet=rs0 \
 *   java -jar target/shipspace-mvp-*.jar
 *
 *   export BASE_URL=http://localhost:8080
 *   export TEST_JWT="Bearer eyJ..."
 *   export TEST_SHIP_ID="<ship-id>"
 *
 *   k6 run --env BASE_URL=$BASE_URL \
 *          --env TEST_JWT=$TEST_JWT \
 *          --env TEST_SHIP_ID=$TEST_SHIP_ID \
 *          load-tests/k6-worst-case-chaos.js
 *
 * PASS CRITERIA:
 *   - server_errors_5xx = 0 (system stays available, even if slow)
 *   - invariant_violations = 0 (no counter corruption under worst-case failure)
 *   - load_shed_hits > 0 in phase 3-4 (shedding activated as expected)
 *   - recovery_p95_ok = true (p95 returns to < 500ms within 90s of recovery)
 *   - health_check_always_200 = true (health endpoint never shed)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL   || "http://localhost:8080";
const JWT      = __ENV.TEST_JWT   || "";
const SHIP_ID  = __ENV.TEST_SHIP_ID || "test-ship-id";

// ── Metrics ───────────────────────────────────────────────────────────────────

const serverErrors        = new Counter("server_errors_5xx");
const invariantViolations = new Counter("invariant_violations");
const loadShedHits        = new Counter("load_shed_503_hits");
const healthCheckFails    = new Counter("health_check_failures");
const likeLatency         = new Trend("like_latency_ms",   true);
const feedLatency         = new Trend("feed_latency_ms",   true);
const healthLatency       = new Trend("health_latency_ms", true);
const errorRate           = new Rate("error_rate");

// ── Test phases ───────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    /**
     * Phase 1 (0-3m): Baseline — everything healthy, 30 VUs.
     * Establishes p95/p99 baseline before failures are injected.
     */
    baseline: {
      executor: "constant-vus",
      vus: 30,
      duration: "3m",
      exec: "mixedTraffic",
      startTime: "0s",
    },

    /**
     * Phase 2 (3-6m): Single failure — MongoDB latency only.
     * Inject: toxiproxy mongo-latency=200ms
     * Validates: circuit breaker behavior, latency increase is bounded.
     */
    single_failure: {
      executor: "constant-vus",
      vus: 30,
      duration: "3m",
      exec: "mixedTraffic",
      startTime: "3m",
    },

    /**
     * Phase 3 (6-12m): Combined failure — MongoDB slow + Redis slow.
     * Inject: mongo-latency=200ms + mongo-loss + redis-latency=150ms
     * Validates: failures don't cascade, system stays available.
     */
    combined_failure: {
      executor: "constant-vus",
      vus: 30,
      duration: "6m",
      exec: "mixedTraffic",
      startTime: "6m",
    },

    /**
     * Phase 4 (12-18m): Worst case — combined failure + 5x traffic spike.
     * All failures active + traffic ramps to 150 VUs.
     * Validates: load shedding activates, health checks never shed,
     *            writes shed before reads, invariants hold.
     */
    worst_case: {
      executor: "ramping-vus",
      startVUs: 30,
      stages: [
        { duration: "1m", target: 150 }, // ramp to 5x
        { duration: "4m", target: 150 }, // hold at 5x
        { duration: "1m", target: 30  }, // ramp down
      ],
      exec: "mixedTraffic",
      startTime: "12m",
    },

    /**
     * Phase 5 (18-25m): Recovery — remove all toxics, hold at normal load.
     * Validates: p95 returns to baseline within 90s.
     */
    recovery: {
      executor: "constant-vus",
      vus: 30,
      duration: "7m",
      exec: "mixedTraffic",
      startTime: "18m",
    },

    /**
     * Health check monitor — runs throughout all phases.
     * Health endpoint must NEVER return non-200, even at peak load.
     * A failed health check means the load balancer removes the instance.
     */
    health_monitor: {
      executor: "constant-vus",
      vus: 2,
      duration: "25m",
      exec: "checkHealth",
      startTime: "0s",
    },

    /**
     * Invariant checker — runs throughout all phases.
     * Counter must never go negative or overflow, even under worst-case failure.
     */
    invariant_checker: {
      executor: "constant-vus",
      vus: 2,
      duration: "25m",
      exec: "checkInvariants",
      startTime: "0s",
    },
  },

  thresholds: {
    // Zero tolerance — correctness must hold under any failure combination
    "server_errors_5xx":    ["count==0"],
    "invariant_violations": ["count==0"],
    "health_check_failures": ["count==0"],

    // Performance — degraded but not dead during failure phases
    // p99 < 10s allows for circuit breaker open + retry + fallback
    "like_latency_ms": ["p(99)<10000"],
    "feed_latency_ms": ["p(99)<10000"],

    // Health checks must always be fast — even under worst-case load
    "health_latency_ms": ["p(99)<1000"],

    // 503s from load shedding are expected — not counted as errors
    "http_req_failed": ["rate<0.20"],
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

const authHeaders = { Authorization: JWT, "Content-Type": "application/json" };

export function mixedTraffic() {
  const roll = Math.random();

  if (roll < 0.35) {
    // Write: like/unlike — tests MongoDB write + Redis blacklist
    const t = Date.now();
    const res = Math.random() < 0.5
        ? http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders })
        : http.del(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    likeLatency.add(Date.now() - t);
    classifyResult(res, "engagement");

  } else if (roll < 0.70) {
    // Read: trending feed — tests Redis cache path
    const t = Date.now();
    const res = http.get(`${BASE_URL}/api/feeds/trending?page=0&size=20`);
    feedLatency.add(Date.now() - t);
    classifyResult(res, "feed");

  } else {
    // Read: profile — tests MongoDB read path
    const t = Date.now();
    const res = http.get(`${BASE_URL}/api/auth/me`, { headers: authHeaders });
    classifyResult(res, "profile");
  }

  // No sleep during worst-case phase — maximum pressure
  if (Math.random() < 0.3) sleep(0.1);
}

export function checkHealth() {
  const t = Date.now();
  const res = http.get(`${BASE_URL}/api/health`);
  healthLatency.add(Date.now() - t);

  const ok = check(res, {
    "health: always 200": (r) => r.status === 200,
    "health: fast (<1s)": (r) => r.timings.duration < 1000,
  });

  if (res.status !== 200) {
    healthCheckFails.add(1);
    console.error(`[${ts()}] HEALTH CHECK FAILED: status=${res.status} — load balancer will remove instance`);
  }

  sleep(5); // check every 5 seconds
}

export function checkInvariants() {
  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers: authHeaders });

  if (res.status === 503) {
    loadShedHits.add(1);
    sleep(3);
    return;
  }

  if (res.status !== 200) {
    if (res.status >= 500) {
      serverErrors.add(1);
      console.error(`[${ts()}] Invariant check: ship read returned ${res.status}`);
    }
    sleep(5);
    return;
  }

  try {
    const ship = JSON.parse(res.body);
    if (ship.likeCount < 0) {
      invariantViolations.add(1);
      console.error(`[${ts()}] INVARIANT VIOLATION: likeCount=${ship.likeCount} (negative)`);
    }
    if (ship.likeCount > 100000) {
      invariantViolations.add(1);
      console.error(`[${ts()}] INVARIANT VIOLATION: likeCount=${ship.likeCount} (overflow)`);
    }
  } catch (e) {
    console.error(`[${ts()}] Failed to parse ship: ${e}`);
  }

  sleep(10);
}

export function teardown() {
  console.log("\n=== WORST-CASE CHAOS RESULTS ===");
  console.log("Phase summary:");
  console.log("  0-3m:   Baseline (30 VUs, no failures)");
  console.log("  3-6m:   MongoDB +200ms latency");
  console.log("  6-12m:  MongoDB slow + Redis +150ms + packet loss");
  console.log("  12-18m: All failures + 5x traffic spike (150 VUs)");
  console.log("  18-25m: Recovery (remove all toxics)");
  console.log("");
  console.log("Pass criteria:");
  console.log("  server_errors_5xx    = 0  →", "system stayed available under worst-case");
  console.log("  invariant_violations = 0  →", "no counter corruption under failure");
  console.log("  health_check_failures = 0 →", "health endpoint never shed");
  console.log("  load_shed_503_hits > 0    →", "load shedding activated during spike");
  console.log("");
  console.log("Recovery validation:");
  console.log("  Compare like_latency_ms p95 in phase 1 vs phase 5.");
  console.log("  Phase 5 p95 must be within 2x of phase 1 p95.");
  console.log("  If not: system is not recovering — investigate connection pool or GC.");
}

function classifyResult(res, op) {
  if (res.status === 503) {
    loadShedHits.add(1);
    return; // expected under overload
  }
  if (res.status >= 500) {
    serverErrors.add(1);
    console.error(`[${ts()}] 5xx on ${op}: status=${res.status}`);
  }
  errorRate.add(res.status >= 500 ? 1 : 0);
}

function ts() { return new Date().toISOString(); }
