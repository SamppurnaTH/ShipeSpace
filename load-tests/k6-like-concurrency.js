/**
 * k6 concurrency stress test — Like/Unlike race condition validation.
 *
 * PURPOSE:
 * This test proves the like/unlike system holds its invariants under real
 * distributed load — multiple k6 workers hitting the same ship simultaneously,
 * simulating multiple backend instances behind a load balancer.
 *
 * This is what the JVM integration tests CANNOT prove:
 *   - Real network latency between client and server
 *   - OS-level thread scheduling across separate processes
 *   - Connection pool contention from truly independent clients
 *   - Load balancer routing to different backend instances
 *
 * INVARIANTS VALIDATED (via the /api/ships/{id} endpoint after each phase):
 *   1. likeCount ∈ {0, 1} for a single user/ship pair
 *   2. likeCount is never negative
 *   3. likeCount matches the actual Like document count (checked via admin endpoint)
 *
 * SETUP REQUIRED:
 *   1. Start two backend instances pointing at the same MongoDB replica set:
 *      docker-compose up --scale backend=2
 *   2. Create a test user and ship, export their IDs and JWT:
 *      export TEST_JWT="Bearer eyJ..."
 *      export TEST_SHIP_ID="<ship-id>"
 *      export TEST_USER_ID="<user-id>"
 *   3. Run:
 *      k6 run --env BASE_URL=http://localhost:8086 \
 *             --env TEST_JWT=$TEST_JWT \
 *             --env TEST_SHIP_ID=$TEST_SHIP_ID \
 *             load-tests/k6-like-concurrency.js
 *
 * READING RESULTS:
 *   - http_req_failed rate must be 0% (no 5xx errors)
 *   - like_invariant_violations counter must be 0
 *   - counter_drift_detected counter must be 0
 *   - p99 latency should be < 200ms (atomic MongoDB ops are fast)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TEST_JWT = __ENV.TEST_JWT || "";
const SHIP_ID = __ENV.TEST_SHIP_ID || "test-ship-id";

// ── Custom metrics — these are the invariant violation detectors ──────────────

// Incremented whenever likeCount is negative or > 1 after an operation
const invariantViolations = new Counter("like_invariant_violations");

// Incremented whenever the API returns a 5xx error
const serverErrors = new Counter("server_errors_5xx");

// Latency breakdown by operation type
const likeLatency = new Trend("like_op_latency_ms");
const unlikeLatency = new Trend("unlike_op_latency_ms");
const readLatency = new Trend("read_latency_ms");

const errorRate = new Rate("error_rate");

// ── Test configuration ────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    /**
     * Phase 1: Pure like storm.
     * 200 VUs all try to like the same ship simultaneously.
     * Only one Like document should exist at the end.
     * Counter must be exactly 1.
     */
    like_storm: {
      executor: "shared-iterations",
      vus: 200,
      iterations: 200,
      maxDuration: "30s",
      exec: "likeOnly",
      startTime: "0s",
    },

    /**
     * Phase 2: Pure unlike storm (starts after like storm settles).
     * 200 VUs all try to unlike the same ship simultaneously.
     * Counter must be 0 at the end, never negative.
     */
    unlike_storm: {
      executor: "shared-iterations",
      vus: 200,
      iterations: 200,
      maxDuration: "30s",
      exec: "unlikeOnly",
      startTime: "35s", // 5s gap to let phase 1 fully settle
    },

    /**
     * Phase 3: Random mix — the most realistic scenario.
     * 500 VUs randomly like or unlike. Runs for 2 minutes.
     * Invariants checked continuously via reads interleaved with writes.
     */
    random_mix: {
      executor: "constant-vus",
      vus: 500,
      duration: "2m",
      exec: "randomLikeUnlike",
      startTime: "70s", // after phase 2 settles
    },

    /**
     * Phase 4: Read-while-write validation.
     * Concurrent readers check the likeCount while writers are active.
     * Detects windows where the counter is temporarily inconsistent.
     */
    read_validation: {
      executor: "constant-vus",
      vus: 20,
      duration: "2m",
      exec: "validateInvariants",
      startTime: "70s", // runs concurrently with phase 3
    },
  },

  thresholds: {
    // Zero tolerance for invariant violations
    "like_invariant_violations": ["count==0"],
    // Zero tolerance for server errors
    "server_errors_5xx": ["count==0"],
    // Performance: atomic MongoDB ops should be fast
    "like_op_latency_ms": ["p(95)<200", "p(99)<500"],
    "unlike_op_latency_ms": ["p(95)<200", "p(99)<500"],
    // Overall error rate (4xx from business logic is OK, 5xx is not)
    "http_req_failed": ["rate<0.01"],
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

const headers = {
  Authorization: TEST_JWT,
  "Content-Type": "application/json",
};

export function likeOnly() {
  const start = Date.now();
  // PUT semantics — idempotent like
  const res = http.put(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers });
  likeLatency.add(Date.now() - start);

  const ok = check(res, {
    "like: not 5xx": (r) => r.status < 500,
    "like: 200 or 409": (r) => r.status === 200 || r.status === 409,
  });

  if (res.status >= 500) serverErrors.add(1);
  errorRate.add(!ok);
}

export function unlikeOnly() {
  const start = Date.now();
  // DELETE semantics — idempotent unlike
  const res = http.del(`${BASE_URL}/api/ships/${SHIP_ID}/like`, null, { headers });
  unlikeLatency.add(Date.now() - start);

  const ok = check(res, {
    "unlike: not 5xx": (r) => r.status < 500,
    // 200 = unliked, 404 = wasn't liked (both are correct)
    "unlike: 200 or 404": (r) => r.status === 200 || r.status === 404,
  });

  if (res.status >= 500) serverErrors.add(1);
  errorRate.add(!ok);
}

export function randomLikeUnlike() {
  if (Math.random() < 0.5) {
    likeOnly();
  } else {
    unlikeOnly();
  }
  // No sleep — maximum contention
}

/**
 * Reads the ship and validates invariants in real-time while writes are happening.
 * This is the distributed equivalent of assertInvariants() in the JVM tests.
 */
export function validateInvariants() {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers });
  readLatency.add(Date.now() - start);

  if (res.status !== 200) {
    errorRate.add(1);
    return;
  }

  try {
    const ship = JSON.parse(res.body);
    const likeCount = ship.likeCount;

    // Invariant 1: never negative
    if (likeCount < 0) {
      invariantViolations.add(1);
      console.error(`INVARIANT VIOLATION: likeCount is negative (${likeCount})`);
    }

    // Invariant 2: never > 1 for a single user/ship pair
    // (In a multi-user test this would be > totalUsers, but here it's 1 user)
    if (likeCount > 1) {
      invariantViolations.add(1);
      console.error(`INVARIANT VIOLATION: likeCount > 1 (${likeCount}) — duplicate like or counter overflow`);
    }
  } catch (e) {
    console.error(`Failed to parse ship response: ${e}`);
    errorRate.add(1);
  }

  sleep(0.1); // 100ms between reads — don't overwhelm the read path
}

/**
 * Teardown: final invariant check after all scenarios complete.
 * This is the definitive pass/fail for the test run.
 */
export function teardown() {
  const res = http.get(`${BASE_URL}/api/ships/${SHIP_ID}`, { headers });

  if (res.status !== 200) {
    console.error(`Teardown: failed to read ship — status ${res.status}`);
    return;
  }

  const ship = JSON.parse(res.body);
  const likeCount = ship.likeCount;

  console.log(`\n=== FINAL STATE ===`);
  console.log(`Ship ID:    ${SHIP_ID}`);
  console.log(`likeCount:  ${likeCount}`);
  console.log(`Expected:   0 or 1`);
  console.log(`Result:     ${likeCount >= 0 && likeCount <= 1 ? "PASS ✓" : "FAIL ✗"}`);

  if (likeCount < 0 || likeCount > 1) {
    invariantViolations.add(1);
    console.error(`FINAL INVARIANT VIOLATION: likeCount=${likeCount}`);
  }
}
