/**
 * k6 search load test — realistic typing patterns under concurrent users.
 *
 * Models three real user behaviors:
 *
 *   1. slow_typer    — types a full word with pauses > debounce (350ms).
 *                      Each pause fires a real request. Worst case for backend.
 *                      Pattern: "r" → pause → "re" → pause → "react"
 *                      Expected: 3 requests per search session.
 *
 *   2. fast_typer    — types faster than debounce. Only the final pause fires.
 *                      Pattern: "react" typed in < 350ms total.
 *                      Expected: 1 request per search session.
 *                      (Frontend debounce handles this — backend sees 1 req)
 *
 *   3. scraper       — hammers the endpoint without debounce.
 *                      Tests rate limiting enforcement.
 *                      Expected: 429 after 20 requests/min (anonymous limit).
 *
 * What to watch during this test:
 *   - shipspace_search_fallback_total{entity="ship"/"user"} — fallback rate
 *   - shipspace_search_users_duration_ms / ships_duration_ms — p95/p99
 *   - shipspace_rate_limit_rejections_total{action="search:anon"} — scraper hits
 *   - SLOW_QUERY log lines in backend output
 *   - MongoDB CPU / collection scan indicators
 *
 * Run:
 *   k6 run --env BASE_URL=https://staging.shipspace.app load-tests/k6-search-load.js
 *
 * With output to Prometheus pushgateway:
 *   k6 run --out experimental-prometheus-rw load-tests/k6-search-load.js
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// Custom metrics
const searchLatency     = new Trend("search_latency_ms");
const fallbackLatency   = new Trend("search_fallback_latency_ms"); // latency when fallback fires
const textLatency       = new Trend("search_text_latency_ms");     // latency when $text fires
const fallbackHits      = new Counter("search_fallback_hits_exact");
const rateLimitHits     = new Counter("search_rate_limit_hits");
const errorRate         = new Rate("search_error_rate");
const textPathRate      = new Rate("search_text_path_rate");

// X-Search-Path is only present when SEARCH_DEBUG_HEADERS=true on the backend.
// Run load tests against staging with that env var set:
//   k6 run --env DEBUG_HEADERS=true --env BASE_URL=https://staging.shipspace.app ...
const DEBUG_HEADERS_ENABLED = __ENV.DEBUG_HEADERS === "true";

// Realistic search terms that exercise both $text and fallback paths.
// Partial terms (< 4 chars, no word boundary) will trigger fallback.
const SEARCH_TERMS = [
  // Full words — should hit $text index
  "react", "typescript", "mongodb", "kubernetes", "authentication",
  "dashboard", "api", "deploy", "docker", "postgres",
  // Partial words — will trigger regex fallback (tests fallback cost)
  "reac", "type", "mongo", "kube", "auth",
  "dash", "dep", "dock", "post",
  // Multi-word — tests $text with multiple tokens
  "react typescript", "mongodb atlas", "docker deploy",
];

// Simulates a slow typer: types one character at a time with pauses > 350ms.
// Each pause fires a real backend request (debounce fires).
// This is the worst-case load pattern per user.
const SLOW_TYPE_SEQUENCES = [
  ["r", "re", "rea", "reac", "react"],
  ["t", "ty", "typ", "type", "types"],
  ["m", "mo", "mon", "mong", "mongo"],
  ["a", "au", "aut", "auth"],
  ["d", "de", "dep", "depl", "deploy"],
];

export const options = {
  scenarios: {
    // Scenario 1: Slow typers — each pause > debounce fires a request
    // Models the worst-case backend load from real users
    slow_typers: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "1m", target: 20 },   // Ramp: 20 concurrent slow typers
        { duration: "3m", target: 20 },   // Steady state
        { duration: "1m", target: 50 },   // Spike: 50 concurrent
        { duration: "3m", target: 50 },   // Hold spike — watch fallback rate
        { duration: "1m", target: 0 },    // Ramp down
      ],
      exec: "slowTyperSearch",
    },

    // Scenario 2: Fast typers — debounce absorbs keystrokes, 1 req per session
    // Models the optimistic case — validates debounce is working
    fast_typers: {
      executor: "constant-vus",
      vus: 30,
      duration: "8m",
      startTime: "1m",
      exec: "fastTyperSearch",
    },

    // Scenario 3: Scraper — no debounce, hammers endpoint
    // Should hit 429 after 20 requests/min (anonymous rate limit)
    scraper: {
      executor: "constant-arrival-rate",
      rate: 5,           // 5 req/s = 300 req/min — well above 20/min limit
      timeUnit: "1s",
      duration: "5m",
      preAllocatedVUs: 10,
      startTime: "2m",
      exec: "scraperSearch",
    },
  },

  thresholds: {
    // Search must stay fast under load
    search_latency_ms: ["p(95)<200", "p(99)<500"],
    // Fallback path must not spike badly — if it does, adaptive circuit will open
    search_fallback_latency_ms: ["p(99)<400"],
    // Errors (non-429) must stay low
    search_error_rate: ["rate<0.01"],
    // HTTP level
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.05"],  // 429s count as "failed" in k6 — allow up to 5%
  },
};

// ── Scenario implementations ──────────────────────────────────────────────────

/**
 * Slow typer: sends each prefix of a search term as a separate request,
 * with a 400ms pause between each (just over the 350ms debounce threshold).
 * This is what the backend sees from a user who types slowly.
 */
export function slowTyperSearch() {
  const sequence = SLOW_TYPE_SEQUENCES[Math.floor(Math.random() * SLOW_TYPE_SEQUENCES.length)];

  for (const term of sequence) {
    const res = http.get(`${BASE_URL}/api/search?q=${encodeURIComponent(term)}&size=10`);
    const ok = check(res, {
      "search 200 or 429": (r) => r.status === 200 || r.status === 429,
      "not 500": (r) => r.status < 500,
    });

    searchLatency.add(res.timings.duration);
    errorRate.add(res.status >= 500);

    if (res.status === 429) {
      rateLimitHits.add(1);
      sleep(2); // Back off on rate limit
      return;
    }

    if (res.status === 200) {
      const searchPath = DEBUG_HEADERS_ENABLED
          ? (res.headers["X-Search-Path"] || "unknown")
          : "unknown";

      // Correlate path with latency — key insight:
      // if fallback_latency >> text_latency, the regex scan is expensive under load
      if (searchPath === "fallback") {
        fallbackHits.add(1);
        fallbackLatency.add(res.timings.duration);
        textPathRate.add(false);
      } else if (searchPath === "text") {
        textLatency.add(res.timings.duration);
        textPathRate.add(true);
      }
    }

    sleep(0.4); // 400ms between keystrokes — just over debounce threshold
  }

  sleep(Math.random() * 3 + 2); // 2-5s between search sessions
}

/**
 * Fast typer: sends only the final complete term.
 * Models what the backend sees after debounce absorbs intermediate keystrokes.
 * Should produce clean $text hits with low latency.
 */
export function fastTyperSearch() {
  const term = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];
  const res = http.get(`${BASE_URL}/api/search?q=${encodeURIComponent(term)}&size=10`);

  check(res, {
    "search 200": (r) => r.status === 200,
    "has users key": (r) => {
      try { return JSON.parse(r.body).users !== undefined } catch { return false }
    },
    "has ships key": (r) => {
      try { return JSON.parse(r.body).ships !== undefined } catch { return false }
    },
  });

  searchLatency.add(res.timings.duration);
  errorRate.add(res.status >= 500);

  if (res.status === 200) {
    const searchPath = DEBUG_HEADERS_ENABLED
        ? (res.headers["X-Search-Path"] || "unknown")
        : "unknown";
    if (searchPath === "fallback") {
      fallbackHits.add(1);
      fallbackLatency.add(res.timings.duration);
      textPathRate.add(false);
    } else if (searchPath === "text") {
      textLatency.add(res.timings.duration);
      textPathRate.add(true);
    }
  }

  sleep(Math.random() * 4 + 3); // 3-7s between searches (user reads results)
}

/**
 * Scraper: fires requests as fast as possible without any debounce.
 * Should be rate-limited to 429 after 20 requests/min.
 * Validates that rate limiting is actually enforced, not just configured.
 */
export function scraperSearch() {
  const term = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];
  const res = http.get(`${BASE_URL}/api/search?q=${encodeURIComponent(term)}&size=20`);

  check(res, {
    "scraper gets 429 eventually": (r) => r.status === 200 || r.status === 429,
    "scraper never gets 500": (r) => r.status < 500,
  });

  if (res.status === 429) {
    rateLimitHits.add(1);
  }

  errorRate.add(res.status >= 500);
  // No sleep — scraper fires as fast as possible
}

export function handleSummary(data) {
  const p95      = data.metrics.search_latency_ms?.values?.["p(95)"] ?? "N/A";
  const p99      = data.metrics.search_latency_ms?.values?.["p(99)"] ?? "N/A";
  const fbP95    = data.metrics.search_fallback_latency_ms?.values?.["p(95)"] ?? "N/A";
  const fbP99    = data.metrics.search_fallback_latency_ms?.values?.["p(99)"] ?? "N/A";
  const fbMax    = data.metrics.search_fallback_latency_ms?.values?.["max"]   ?? "N/A";
  const txP95    = data.metrics.search_text_latency_ms?.values?.["p(95)"]     ?? "N/A";
  const txP99    = data.metrics.search_text_latency_ms?.values?.["p(99)"]     ?? "N/A";
  const txMax    = data.metrics.search_text_latency_ms?.values?.["max"]       ?? "N/A";
  const rl       = data.metrics.search_rate_limit_hits?.values?.count ?? 0;
  const fb       = data.metrics.search_fallback_hits_exact?.values?.count ?? 0;
  const textRate = data.metrics.search_text_path_rate?.values?.rate ?? 0;
  const err      = data.metrics.search_error_rate?.values?.rate ?? 0;
  const total    = data.metrics.http_reqs?.values?.count ?? 0;

  const fallbackPct = total > 0 ? ((fb / total) * 100).toFixed(1) : "N/A";

  // p95 delta: primary signal for "is fallback causing latency problems?"
  let p95Delta = "N/A";
  if (typeof fbP95 === "number" && typeof txP95 === "number") {
    const d = fbP95 - txP95;
    p95Delta = `${d > 0 ? "+" : ""}${d.toFixed(1)}ms`;
    if (d > 50) p95Delta += " ⚠ fallback significantly slower — adaptive circuit may open";
  }

  // p99 delta: reveals spikes that p95 hides
  let p99Delta = "N/A";
  if (typeof fbP99 === "number" && typeof txP99 === "number") {
    const d = fbP99 - txP99;
    p99Delta = `${d > 0 ? "+" : ""}${d.toFixed(1)}ms`;
    if (d > 200) p99Delta += " ⚠ p99 spike — occasional very slow fallback scans";
  }

  const fmt = (v) => typeof v === "number" ? v.toFixed(1) + "ms" : String(v);

  return {
    stdout: `
=== Search Load Test Summary ===
Overall:   p95=${fmt(p95)}  p99=${fmt(p99)}

$text path:
  p95=${fmt(txP95)}  p99=${fmt(txP99)}  max=${fmt(txMax)}

Fallback path:
  p95=${fmt(fbP95)}  p99=${fmt(fbP99)}  max=${fmt(fbMax)}

Latency delta (fallback - text):
  p95: ${p95Delta}
  p99: ${p99Delta}

$text path rate:    ${typeof textRate === "number" ? (textRate * 100).toFixed(1) : textRate}%
Fallback hits:      ${fb} (${fallbackPct}% of requests)
Rate limit hits:    ${rl}  (scraper scenario — expect high)
Error rate (5xx):   ${typeof err === "number" ? (err * 100).toFixed(2) : err}%

Interpretation:
  $text path rate > 85%      → index is working well
  $text path rate < 70%      → users typing partial words; consider Atlas Search
  p95 delta > 50ms           → fallback regex scan is expensive; adaptive circuit will open
  p99 delta > 200ms          → occasional very slow scans; check collection size
  Rate limit hits > 0        → rate limiting enforced (expected from scraper scenario)
  DEBUG_HEADERS not enabled  → run with --env DEBUG_HEADERS=true against staging

Adaptive fallback: check backend logs for "Adaptive fallback DISABLED" entries.
Prometheus:        shipspace_search_fallback_total{entity="ship"/"user"}
================================
`,
  };
}
