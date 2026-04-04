# Go-Live Checklist

Operator-proof. Run this before first traffic and after every spike.
If you're tired, go slower — not faster.

---

## Hard Gate — Before First Traffic

Do not proceed until every item is green.

### Control loop baseline
```
shipspace.slo.degradation_level        = 0
shipspace.slo.feed_degradation_level   = 0
shipspace.slo.engage_degradation_level = 0
shipspace.slo.system_degradation_level = 0
shipspace.slo.adaptive_concurrency     = 200  (or configured max)
shipspace.slo.rate_limit_multiplier    = 1.0
shipspace.slo.warm_start_remaining     → 0 within 60s of startup
```

### Smoke test
Send one request manually:
- Response: 200
- MongoDB hit confirmed (check logs or `mongodb.driver.commands` metric)
- Latency: within expected p95 baseline

### Health endpoint
```
GET /api/health → 200, latency < 200ms
GET /api/actuator/health/readiness → UP
GET /api/actuator/health/liveness  → UP
```

**If anything above fails: stop. Fix it. Do not open traffic.**

---

## First Traffic Spike — Expected Behavior

This will happen. Know what healthy looks like before it does.

| Metric | Healthy | Not Healthy |
|--------|---------|-------------|
| `adaptive_concurrency` | Dips gradually, never instant floor | Drops to min immediately |
| `rate_limit_multiplier` | Steps 1.0 → 0.70 → 0.50 | Jumps straight to 0.33 |
| 503 rate | Low single digits % | Sustained > 10% |
| p95 latency | Rises then stabilizes | Spirals upward continuously |
| `degradation_level` | Smooth transitions (0→1→2) | Oscillates 0→2→0→2 |

**Oscillation (0→2→0→2) means hysteresis needs tuning. Don't touch it during the spike. Note it, fix it after.**

---

## Recovery Phase — Most Important Check

After load drops, watch these in order:

1. `adaptive_concurrency` climbs back in ~5% steps
2. `rate_limit_multiplier` returns to 1.0
3. All `*_degradation_level` gauges return to 0

**If system stays degraded after load is gone:**
- Recovery streak threshold (9) may be too strict
- Or violation rate threshold (10%) is still being exceeded
- Wait 5 minutes before touching anything

**If system recovers too fast and re-degrades immediately:**
- Hysteresis is too weak
- Increase `RECOVER_STREAK` or `RECOVER_RATE` threshold

---

## Two Numbers That Override Everything Else

### `like_invariant_violations` — data integrity
```
Must be 0 at all times.
```
If this is > 0: stop traffic, investigate immediately.
This is not a performance issue. This is a correctness issue.

### `server_errors_5xx` — system health
```
Must be 0 at all times.
```
**Critical distinction:**
- `503` = load shedding = expected, correct behavior
- `5xx` = system failure = stop and investigate

Do not confuse these. 503s during a spike are the system working. 500s are the system broken.

---

## Health Endpoint During Load

`/api/health` must:
- Return 200 at all times, including peak load
- p99 latency < 1000ms even under worst-case traffic

If health checks fail → load balancer ejects the instance → all traffic routes to remaining instances → they also fail health checks → cascade.

This is the one failure mode that takes down the entire service, not just one instance.

---

## The One Rule When Something Looks Wrong

```
1. Observe metrics
2. Identify the pattern
3. Confirm the cause
4. Change ONE variable
5. Observe again
```

Not two variables. Not a "quick fix". One variable, then observe.

Most production incidents are caused by well-intentioned changes made under pressure without enough data. The system is observable. Use it.

---

## What Not To Touch In The First 48 Hours

Unless something is clearly broken (invariant violations or 5xx errors):

- Do not change window size
- Do not change DEGRADE_STREAK or RECOVER_STREAK
- Do not change DEGRADE_RATE or RECOVER_RATE thresholds
- Do not change rate limit steps
- Do not change warm-start cycles

You need real traffic data before any of these make sense to tune.
Instinct-based tuning before data is how you break a working system.

---

## Tuning Reference (After You Have Data)

Only open this section after 48+ hours of real traffic.

| Symptom | Likely cause | Adjustment |
|---------|-------------|------------|
| Constant oscillation | Hysteresis too weak | Increase RECOVER_STREAK (9 → 12) |
| Slow to degrade | DEGRADE_RATE too high | Lower from 0.40 → 0.30 |
| Stays degraded too long | RECOVER_RATE too strict | Raise from 0.10 → 0.15 |
| Too many 503s under normal load | Concurrency limit too low | Raise max-concurrent-requests |
| Rate limits too aggressive | Steps too steep | Adjust RATE_STEPS[1] from 0.70 → 0.80 |

Change one. Observe for 30 minutes. Then decide if another change is needed.
