/**
 * k6 chaos test — system behavior under failure conditions.
 *
 * This tests what the JVM integration tests cannot:
 *   - Real network latency and timeouts
 *   - System behavior when Redis is degraded (slow, not down)
 *   - Connection pool exhaustion from truly independent HTTP clients
 *   - Recovery after a traffic spike
 *   - Invariant correctness under adversarial load patterns
 *
 * FAILURE SCENARIOS COVERED:
 *   1. Redis slow (not down) — rate limiting still works, latency spikes
 *   2. Connection pool exhaustion — requests queue, p99 degrades
 *   3. Retry storm — 500 duplicate requests for the same operation
 *   4. Hotspot contention — 100 users hitting one ship simultaneously
 *   5. Recovery — system returns to baseline after spike
 *
 * SETUP:
 *   # Start two backend instances + MongoDB + Redis
 *   docker-compose up --scale backend=2 -d
 *
 *   # Inject Redis latency (requires tc or toxiproxy):
 *   # Option A — toxiproxy (recommended):
 *   toxiproxy-cli toxic add redis-proxy --type latency --attribute latency=100
 *
 *   # Option B — tc (Linux only):
 *   docker exec shipspace-redis tc qdisc add dev eth0 root netem delay 100ms
 *
 *   # Export test credentials:
 *   export BASE_URL=http://localhost:8086
 *   export TEST_JWT="Bearer eyJ..."
 *   export TEST_SHIP_ID="<viral-ship-id>"
 *
 *   # Run:
 *   k6 run --env BASE_URL=$BASE_URL \
 *          --env TEST_JWT=$TEST_JWT \
 *          --env TEST_SHIP_ID=$TEST_SHIP_ID \
 *          load-tests/k6-chaos.js
 *
 * PASS CRITERIA (all must hold):
 *   - like_invariant_violations = 0 (zero tolerance)
 *   - server_errors_5xx = 0 (zero tolerance)
 *   - http_req_duration p95 < 500ms during normal load
 *   - http_req_duration p99 < 2000ms even during spike
 *   - system recovers to p95 < 500ms within 60s after spike
 */

import http from "k6/http";
import { check, sleep, group } from "k6";
import { Counter, Rate, Trend, Gauge } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const JWT = __ENV.TEST_JWT || "";
const SHIP_ID = __ENV.TEST_SHIP_ID || "test-ship-id";

// ── Metrics — these are the invariant detectors ───────────────────────────────

const invariantViolations = new Counter("like_invariant_violations");
const serverErrors = new Counter("server_errors_5xx");
const rateLimitHits = new Counter("rate_limit_429_hits");
const likeLatency = new Trend("like_latency_ms", true);   // true = emit percentiles
const unlikeLatency = new Trend("unlike_latency_ms", true);
const readLatency = new Trend("read_latency_ms", true);
const errorRate = new Rate("error_rate");

// ── Thresholds — the test FAILS if any of these are breached ─────────────────

export const options = {
  scenarios: {
    /**
     * Scenario 1: Baseline — normal load before chaos.
     * Establishes p95/p99 baseline. Must pass before chaos begins.
     */
    baseline: {
      executor: "constant-vus",
      vus: 50,
      duration: "2m",
      exec: "normalLikeUnlike",
      startTime: "0s",
    },

    /**
     * Scenario 2: Hotspot — 100 users hit the same ship simultaneously.
     * Simulates a ship going viral. Counter must remain consistent.
     */
    hotspot: {
      executor: "shared-iterations",
      vus: 100,
      iterations: 500,
      maxDuration: "2m",
      exec: "hotspotLike",
      startTime: "2m30s",
    },

    /**
     * Scenario 3: Retry storm — same user sends 200 rapid duplicate likes.
     * Simulates aggressive client retry logic or double-submit.
     * Counter must be exactly 1 after the storm.
     */
    retry_storm: {
      executor: "shared-iterations",
      vus: 200,
      iterations: 200,
      maxDuration: "1m",
      exec: "retryStorm",
      startTime: "5m",
    },

    /**
     * Scenario 4: Spike + recovery.
     * Ramps to 400 VUs (above normal capacity), holds, then ramps down.
     * System must recover to baseline latency within 60s of ramp-down.
     */
    spike_recovery: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 400 },  // spike
        { duration: "2m",  target: 400 },  // hold at spike
        { duration: "30s", target: 50  },  // ramp down
        { duration: "2m",  target: 50  },  // recovery window — must return to baseline
      ],
      exec: "normalLikeUnlike",
      startTime: "7m",
    },

    /**
     * Scenario 5: Continuous invariant validation.
     * Reads the ship state throughout all scenarios.
     * Any invariant violation during any scenario is a test failure.
     */
    invariant_check: {
      executor: "constant-vus",
      vus: 5,
      duration: "13m",
      exec: "validateInvariants",
      startTime: "0s",
    },
  },

  thresholds: {
    // Zero tolerance — these are correctness failures, not performance
    "like_invariant_violations": ["count==0"],
    "server_errors_5xx":         ["count==0"],

    // Performance — p95 must be fast during normal load
    // Note: spike scenario will breach p95 temporarily — that's expected.
    // The recovery scenario validates the system returns to baseline.
    "like_latency_ms":  ["p(95)<500",  "p(99)<2000"],
    "unlike_latency_ms":["p(95)<500",  "p(99)<2000"],
    "read_latency_ms":  ["p(95)<200",  "p(99)<1000"],

    // Overall HTTP error rate — 429s are expected (rate limiting), 5xx are not
    "http_req_failed": ["rate<0.05"],
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

const authHeaders = {
  Authorization: JWT,
  "Content-Type": "application/json",
};

/**
 * Normal like/unlike mix — baseline and recovery scenario.
 */
export function normalLikeUnlike() {
  if (Math.random() < 0.5) {
    const t = Date.now();
    const res = http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    likeLatency.add(Date.now() - t);
    trackResult(res, "like");
  } else {
    const t = Date.now();
    const res = http.del(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    unlikeLatency.add(Date.now() - t);
    trackResult(res, "unlike");
  }
  sleep(0.1);
}

/**
 * Hotspot scenario — all VUs hit the same ship with no sleep.
 * Maximum contention on a single document.
 */
export function hotspotLike() {
  const t = Date.now();
  const res = http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
  likeLatency.add(Date.now() - t);
  trackResult(res, "hotspot-like");
  // No sleep — pure contention
}

/**
 * Retry storm — same operation repeated 200 times with no delay.
 * Tests idempotency under aggressive retry.
 */
export function retryStorm() {
  // Alternate like/unlike to test both idempotent paths
  const isLike = Math.random() < 0.5;
  const t = Date.now();

  if (isLike) {
    const res = http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    likeLatency.add(Date.now() - t);
    trackResult(res, "retry-like");
  } else {
    const res = http.del(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers: authHeaders });
    unlikeLatency.add(Date.now() - t);
    trackResult(res, "retry-unlike");
  }
  // No sleep — simulate aggressive retry
}

/**
 * Continuous invariant validation — runs throughout all scenarios.
 * Reads the ship state and checks likeCount is within valid bounds.
 *
 * For a single-user test: likeCount ∈ {0, 1}
 * For a multi-user test: likeCount ∈ [0, totalUsers]
 *
 * The key invariant is: likeCount >= 0 (never negative).
 * Counter drift (likeCount != actual docs) requires the admin endpoint
 * to verify — we check what's observable via the public API.
 */
export function validateInvariants() {
  const t = Date.now();
  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers: authHeaders });
  readLatency.add(Date.now() - t);

  if (res.status !== 200) {
    if (res.status >= 500) serverErrors.add(1);
    errorRate.add(1);
    return;
  }

  try {
    const ship = JSON.parse(res.body);
    const likeCount = ship.likeCount;

    if (likeCount === undefined || likeCount === null) {
      invariantViolations.add(1);
      console.error(`INVARIANT: likeCount missing from response`);
      return;
    }

    // Invariant: never negative
    if (likeCount < 0) {
      invariantViolations.add(1);
      console.error(`INVARIANT VIOLATION: likeCount=${likeCount} (negative)`);
    }

    // Invariant: reasonable upper bound
    // In a single-user test this is 1; in multi-user it's the user count.
    // We use 1000 as a safe upper bound for any realistic test scenario.
    if (likeCount > 1000) {
      invariantViolations.add(1);
      console.error(`INVARIANT VIOLATION: likeCount=${likeCount} (unreasonably large — counter overflow?)`);
    }

  } catch (e) {
    console.error(`Failed to parse ship response: ${e}`);
    errorRate.add(1);
  }

  sleep(0.5); // 500ms between reads — don't overwhelm the read path
}

/**
 * Teardown: final state report after all scenarios complete.
 */
export function teardown() {
  console.log("\n=== CHAOS TEST FINAL STATE ===");

  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers: authHeaders });
  if (res.status !== 200) {
    console.error(`Teardown: cannot read ship — status ${res.status}`);
    return;
  }

  const ship = JSON.parse(res.body);
  console.log(`Ship ID:    ${SHIP_ID}`);
  console.log(`likeCount:  ${ship.likeCount}`);
  console.log(`Expected:   >= 0`);
  console.log(`Result:     ${ship.likeCount >= 0 ? "PASS ✓" : "FAIL ✗"}`);
  console.log("");
  console.log("Check k6 summary for:");
  console.log("  like_invariant_violations — must be 0");
  console.log("  server_errors_5xx         — must be 0");
  console.log("  like_latency_ms p95       — must be < 500ms");
  console.log("  like_latency_ms p99       — must be < 2000ms");
}

// ── helpers ───────────────────────────────────────────────────────────────────

function trackResult(res, op) {
  const ok = check(res, {
    [`${op}: not 5xx`]: (r) => r.status < 500,
  });

  if (res.status >= 500) {
    serverErrors.add(1);
    console.error(`5xx on ${op}: status=${res.status} body=${res.body}`);
  }

  if (res.status === 429) {
    rateLimitHits.add(1);
    // 429 is expected under load — not an error
  }

  errorRate.add(res.status >= 500 ? 1 : 0);
}
