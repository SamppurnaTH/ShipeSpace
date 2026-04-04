/**
 * k6 stress test — find the breaking point.
 *
 * Ramps VUs until the system degrades. Watch for:
 *   - p99 latency crossing 2s
 *   - Error rate crossing 1%
 *   - MongoDB connection pool exhaustion
 *   - Redis timeout errors in logs
 *
 * Run:
 *   k6 run --env BASE_URL=https://staging.shipspace.app load-tests/k6-stress-test.js
 */

import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  stages: [
    { duration: "2m", target: 50 },
    { duration: "2m", target: 100 },
    { duration: "2m", target: 200 },
    { duration: "2m", target: 300 },
    { duration: "2m", target: 400 },  // Expected breaking point
    { duration: "5m", target: 400 },  // Hold at breaking point
    { duration: "3m", target: 0 },    // Recovery — does the system recover?
  ],
  thresholds: {
    // These are intentionally loose — we want to observe, not fail early
    http_req_duration: ["p(99)<10000"],
    http_req_failed: ["rate<0.5"],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/feeds/trending?page=0&size=20`);
  check(res, { "status 200": (r) => r.status === 200 });
  sleep(1);
}
