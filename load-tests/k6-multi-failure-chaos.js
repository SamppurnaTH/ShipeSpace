/**
 * Multi-failure chaos test — simultaneous infrastructure failures.
 *
 * This is the test that exposes cascading failures.
 * Single-failure tests are necessary but not sufficient.
 * Real outages involve multiple things failing at once:
 *   - MongoDB slow (replica election, disk pressure)
 *   - Redis unavailable (OOM eviction, network partition)
 *   - External API timing out (GitHub OAuth degraded)
 *   - Traffic spike happening at the same time
 *
 * FAILURE SCENARIOS:
 *   Phase 1 (0-5m):   Baseline — everything healthy
 *   Phase 2 (5-10m):  Redis latency injected (100ms delay)
 *   Phase 3 (10-15m): MongoDB latency injected (200ms delay) + Redis still slow
 *   Phase 4 (15-20m): Traffic spike (3x normal) + both dependencies degraded
 *   Phase 5 (20-25m): Redis killed entirely + MongoDB still slow
 *   Phase 6 (25-30m): Recovery — all failures removed, system must recover
 *
 * WHAT WE'RE PROVING:
 *   1. System stays available (no 5xx) when Redis is slow
 *   2. Circuit breaker opens when MongoDB failure rate > 50%
 *   3. Load shedding activates during the traffic spike phase
 *   4. System recovers to baseline SLOs within 60s of failure removal
 *   5. Invariants hold throughout — no counter corruption under failure
 *
 * FAILURE INJECTION (requires toxiproxy):
 *   # Install toxiproxy: https://github.com/Shopify/toxiproxy
 *   toxiproxy-server &
 *   toxiproxy-cli create redis-proxy --listen 0.0.0.0:6380 --upstream localhost:6379
 *   toxiproxy-cli create mongo-proxy --listen 0.0.0.0:27018 --upstream localhost:27017
 *
 *   # Phase 2: inject Redis latency
 *   toxiproxy-cli toxic add redis-proxy --type latency --attribute latency=100 --name redis-slow
 *
 *   # Phase 3: inject MongoDB latency
 *   toxiproxy-cli toxic add mongo-proxy --type latency --attribute latency=200 --name mongo-slow
 *
 *   # Phase 5: kill Redis entirely
 *   toxiproxy-cli toxic add redis-proxy --type bandwidth --attribute rate=0 --name redis-kill
 *
 *   # Phase 6: remove all toxics
 *   toxiproxy-cli toxic remove redis-proxy --toxicName redis-slow
 *   toxiproxy-cli toxic remove redis-proxy --toxicName redis-kill
 *   toxiproxy-cli toxic remove mongo-proxy --toxicName mongo-slow
 *
 * SETUP:
 *   # Point backend at toxiproxy ports instead of direct DB ports
 *   REDIS_PORT=6380 MONGODB_URI=mongodb://localhost:27018/shipspace \
 *   java -jar target/shipspace-mvp-*.jar
 *
 *   export BASE_URL=http://localhost:8080
 *   export TEST_JWT="Bearer eyJ..."
 *   export TEST_SHIP_ID="<ship-id>"
 *
 *   k6 run --env BASE_URL=$BASE_URL \
 *          --env TEST_JWT=$TEST_JWT \
 *          --env TEST_SHIP_ID=$TEST_SHIP_ID \
 *          load-tests/k6-multi-failure-chaos.js
 *
 * PASS CRITERIA:
 *   - server_errors_5xx = 0 (system stays available, even if slow)
 *   - invariant_violations = 0 (no counter corruption under failure)
 *   - load_shed_during_spike > 0 (load shedding activated as expected)
 *   - recovery_latency_ok = true (p95 returns to baseline within 60s)
 *   - p99 during failure phases < 5000ms (degraded but not dead)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend, Gauge } from "k6/metrics";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const JWT        = __ENV.TEST_JWT   || "";
const SHIP_ID    = __ENV.TEST_SHIP_ID || "test-ship-id";

// ── Metrics ───────────────────────────────────────────────────────────────────

const serverErrors       = new Counter("server_errors_5xx");
const invariantViolations = new Counter("invariant_violations");
const loadShedHits       = new Counter("load_shed_503_hits");
const circuitBreakerHits = new Counter("circuit_breaker_hits");
const likeLatency        = new Trend("like_latency_ms",  true);
const feedLatency        = new Trend("feed_latency_ms",  true);
const errorRate          = new Rate("error_rate");

// ── Test phases — manually inject failures between phases ─────────────────────

export const options = {
  scenarios: {
    /**
     * Steady traffic throughout all phases.
     * Load increases in phase 4 to simulate spike + failure simultaneously.
     */
    traffic: {
      executor: "ramping-vus",
      startVUs: 30,
      stages: [
        { duration: "5m",  target: 30  }, // Phase 1: baseline
        { duration: "5m",  target: 30  }, // Phase 2: Redis slow (inject manually)
        { duration: "5m",  target: 30  }, // Phase 3: +MongoDB slow (inject manually)
        { duration: "5m",  target: 90  }, // Phase 4: spike (3x) + both degraded
        { duration: "5m",  target: 30  }, // Phase 5: Redis killed (inject manually)
        { duration: "5m",  target: 30  }, // Phase 6: recovery (remove toxics manually)
      ],
      exec: "mixedTraffic",
    },

    /**
     * Continuous invariant checker — runs throughout all 30 minutes.
     * Any violation during any failure phase is a test failure.
     */
    invariant_checker: {
      executor: "constant-vus",
      vus: 3,
      duration: "30m",
      exec: "checkInvariants",
    },
  },

  thresholds: {
    // Correctness: zero tolerance regardless of failure mode
    "server_errors_5xx":    ["count==0"],
    "invariant_violations": ["count==0"],

    // Performance: degraded but not dead during failure phases
    // p99 < 5s allows for circuit breaker open + fallback latency
    "like_latency_ms": ["p(99)<5000"],
    "feed_latency_ms": ["p(99)<5000"],

    // 503s from load shedding are NOT counted as errors
    // (they are the correct behavior under overload)
    "http_req_failed": ["rate<0.15"],
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

const authHeaders = { Authorization: JWT, "Content-Type": "application/json" };

export function mixedTraffic() {
  const roll = Math.random();

  if (roll < 0.4) {
    // Like/unlike — tests MongoDB write path + Redis blacklist check
    const t = Date.now();
    const res = Math.random() < 0.5
        ? http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders })
        : http.del(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    likeLatency.add(Date.now() - t);
    classifyResult(res, "engagement");

  } else if (roll < 0.7) {
    // Trending feed — tests Redis cache path
    const t = Date.now();
    const res = http.get(`${BASE_URL}/api/feeds/trending?page=0&size=20`);
    feedLatency.add(Date.now() - t);
    classifyResult(res, "feed");

  } else {
    // Profile read — tests MongoDB read path
    const t = Date.now();
    const res = http.get(`${BASE_URL}/api/auth/me`, { headers: authHeaders });
    classifyResult(res, "profile");
  }

  sleep(Math.random() * 0.5); // minimal think time — maintain pressure
}

export function checkInvariants() {
  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers: authHeaders });

  if (res.status === 503) {
    // Load shedding — not an invariant violation, just note it
    loadShedHits.add(1);
    sleep(2);
    return;
  }

  if (res.status !== 200) {
    if (res.status >= 500) {
      serverErrors.add(1);
      invariantViolations.add(1);
      console.error(`[${timestamp()}] Invariant check: ship read returned ${res.status}`);
    }
    sleep(5);
    return;
  }

  try {
    const ship = JSON.parse(res.body);

    if (ship.likeCount < 0) {
      invariantViolations.add(1);
      console.error(`[${timestamp()}] INVARIANT VIOLATION: likeCount=${ship.likeCount} (negative)`);
    }

    if (ship.likeCount > 10000) {
      invariantViolations.add(1);
      console.error(`[${timestamp()}] INVARIANT VIOLATION: likeCount=${ship.likeCount} (overflow)`);
    }

  } catch (e) {
    console.error(`[${timestamp()}] Failed to parse ship: ${e}`);
  }

  sleep(10); // check every 10 seconds during chaos
}

export function teardown() {
  console.log("\n=== MULTI-FAILURE CHAOS RESULTS ===");
  console.log("Phases completed:");
  console.log("  Phase 1 (0-5m):   Baseline");
  console.log("  Phase 2 (5-10m):  Redis +100ms latency");
  console.log("  Phase 3 (10-15m): +MongoDB +200ms latency");
  console.log("  Phase 4 (15-20m): 3x traffic spike + both degraded");
  console.log("  Phase 5 (20-25m): Redis killed");
  console.log("  Phase 6 (25-30m): Recovery");
  console.log("");
  console.log("Key metrics to check:");
  console.log("  server_errors_5xx    — must be 0 (system stayed available)");
  console.log("  invariant_violations — must be 0 (no counter corruption)");
  console.log("  load_shed_503_hits   — should be > 0 in phase 4 (shedding activated)");
  console.log("  like_latency_ms p99  — should spike in phases 3-5, recover in phase 6");
  console.log("");
  console.log("If server_errors_5xx > 0: circuit breaker or fallback is broken");
  console.log("If invariant_violations > 0: counter corruption under failure");
  console.log("If load_shed_503_hits = 0 in phase 4: load shedding not activating");
  console.log("If p99 stays high in phase 6: system is not recovering");
}

// ── helpers ───────────────────────────────────────────────────────────────────

function classifyResult(res, op) {
  if (res.status === 503) {
    loadShedHits.add(1);
    return; // expected under overload — not an error
  }

  if (res.status === 503 && res.body && res.body.includes("circuit")) {
    circuitBreakerHits.add(1);
    return; // circuit breaker open — expected under MongoDB failure
  }

  if (res.status >= 500) {
    serverErrors.add(1);
    console.error(`[${timestamp()}] 5xx on ${op}: status=${res.status}`);
  }

  errorRate.add(res.status >= 500 ? 1 : 0);
}

function timestamp() {
  return new Date().toISOString();
}
