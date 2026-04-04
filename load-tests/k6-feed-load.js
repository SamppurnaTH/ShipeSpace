/**
 * k6 load test — Feed endpoints under realistic traffic.
 *
 * Scenarios:
 *   1. trending_feed    — anonymous users browsing trending (highest volume)
 *   2. personalized_feed — authenticated users (warm path)
 *   3. auth_flow        — login + token refresh (lower volume, high impact)
 *   4. engagement_burst — like/comment spike (simulates viral content)
 *
 * Run:
 *   k6 run --env BASE_URL=https://staging.shipspace.app load-tests/k6-feed-load.js
 *
 * Thresholds (fail the test if breached):
 *   - p95 < 500ms for feed endpoints
 *   - p99 < 2000ms for all endpoints
 *   - error rate < 1%
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TEST_JWT = __ENV.TEST_JWT || ""; // Pre-generated test JWT for authenticated scenarios

// Custom metrics
const feedLatency = new Trend("feed_latency_ms");
const authLatency = new Trend("auth_latency_ms");
const errorRate = new Rate("error_rate");

export const options = {
  scenarios: {
    // Scenario 1: Trending feed — anonymous, high volume
    trending_feed: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "2m", target: 50 },   // Ramp up
        { duration: "5m", target: 50 },   // Steady state
        { duration: "2m", target: 100 },  // Spike
        { duration: "3m", target: 100 },  // Hold spike
        { duration: "2m", target: 0 },    // Ramp down
      ],
      exec: "trendingFeed",
    },

    // Scenario 2: Personalized feed — authenticated users
    personalized_feed: {
      executor: "constant-vus",
      vus: 20,
      duration: "10m",
      exec: "personalizedFeed",
      startTime: "2m", // Start after trending ramp-up
    },

    // Scenario 3: Auth flow — login attempts
    auth_flow: {
      executor: "constant-arrival-rate",
      rate: 5,          // 5 requests/second
      timeUnit: "1s",
      duration: "10m",
      preAllocatedVUs: 10,
      exec: "authFlow",
      startTime: "1m",
    },

    // Scenario 4: Engagement burst — simulates viral ship
    engagement_burst: {
      executor: "ramping-arrival-rate",
      startRate: 1,
      timeUnit: "1s",
      stages: [
        { duration: "1m", target: 1 },
        { duration: "30s", target: 50 },  // Sudden burst
        { duration: "2m", target: 50 },
        { duration: "30s", target: 1 },
      ],
      preAllocatedVUs: 60,
      exec: "engagementBurst",
      startTime: "5m",
    },
  },

  thresholds: {
    // Feed endpoints must be fast
    feed_latency_ms: ["p(95)<500", "p(99)<2000"],
    // Overall error rate
    error_rate: ["rate<0.01"],
    // HTTP request duration
    http_req_duration: ["p(95)<1000", "p(99)<3000"],
    // No more than 1% of requests should fail
    http_req_failed: ["rate<0.01"],
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

export function trendingFeed() {
  const page = Math.floor(Math.random() * 5); // Pages 0-4
  const res = http.get(`${BASE_URL}/api/feeds/trending?page=${page}&size=20`);

  const ok = check(res, {
    "trending feed 200": (r) => r.status === 200,
    "has content": (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.content !== undefined;
      } catch {
        return false;
      }
    },
  });

  feedLatency.add(res.timings.duration);
  errorRate.add(!ok);
  sleep(Math.random() * 2 + 1); // 1-3s think time
}

export function personalizedFeed() {
  if (!TEST_JWT) {
    // Fall back to trending if no JWT provided
    trendingFeed();
    return;
  }

  const headers = { Authorization: `Bearer ${TEST_JWT}` };
  const res = http.get(`${BASE_URL}/api/feeds/personalized?page=0&size=20`, { headers });

  const ok = check(res, {
    "personalized feed 200": (r) => r.status === 200,
    "not 401": (r) => r.status !== 401,
  });

  feedLatency.add(res.timings.duration);
  errorRate.add(!ok);
  sleep(Math.random() * 3 + 2); // 2-5s think time (users read content)
}

export function authFlow() {
  // Test the state endpoint (no actual GitHub code — just rate limit behavior)
  const res = http.get(`${BASE_URL}/api/auth/state`);

  const ok = check(res, {
    "state endpoint 200 or 429": (r) => r.status === 200 || r.status === 429,
    "has state token or rate limit": (r) => {
      if (r.status === 200) {
        try {
          const body = JSON.parse(r.body);
          return body.state !== undefined;
        } catch {
          return false;
        }
      }
      return r.status === 429; // Rate limited is expected under load
    },
  });

  authLatency.add(res.timings.duration);
  errorRate.add(res.status >= 500); // Only count 5xx as errors
  sleep(1);
}

export function engagementBurst() {
  if (!TEST_JWT) return;

  // Simulate users liking a popular ship
  const shipId = __ENV.VIRAL_SHIP_ID || "test-ship-id";
  const headers = {
    Authorization: `Bearer ${TEST_JWT}`,
    "Content-Type": "application/json",
  };

  // 70% likes, 30% comments
  if (Math.random() < 0.7) {
    const res = http.put(`${BASE_URL}/api/ships/${shipId}/like`, null, { headers });
    check(res, {
      "like 200 or 404": (r) => r.status === 200 || r.status === 404,
      "not 500": (r) => r.status < 500,
    });
    errorRate.add(res.status >= 500);
  } else {
    const payload = JSON.stringify({ content: "Great work! This is really impressive." });
    const res = http.post(`${BASE_URL}/api/ships/${shipId}/comments`, payload, { headers });
    check(res, {
      "comment 201 or 404": (r) => r.status === 201 || r.status === 404,
      "not 500": (r) => r.status < 500,
    });
    errorRate.add(res.status >= 500);
  }

  sleep(0.5);
}
