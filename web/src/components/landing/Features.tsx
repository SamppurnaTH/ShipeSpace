import { forwardRef } from 'react'

const features = [
  { title: 'Adaptive feed modes', body: 'Three modes: personalized, trending, fallback. Switches automatically based on system health. X-Feed-Mode header tells the frontend which mode is active on every request.', tag: 'Feed' },
  { title: 'SLO enforcement loop', body: 'Scheduled control loop monitors p95 latency and error rates every 10 seconds. Progressively degrades across 5 levels. Recovers automatically when metrics improve.', tag: 'Resilience' },
  { title: 'Adaptive rate limiting', body: 'Per-user limits enforced at the service layer. Multiplier tied to SLO state: 1.0x normal → 0.33x under load. Remaining quota returned on every response.', tag: 'Rate limiting' },
  { title: 'Atomic engagement counters', body: 'Like and comment counts use MongoDB atomic $inc. No read-modify-write race conditions. Verified correct under 500 concurrent threads in chaos testing.', tag: 'Correctness' },
  { title: 'Live debug panel', body: 'Every API response captured with latency, status, feed mode, and rate limit state. Filterable by errors or slow requests. Built into the frontend — zero configuration.', tag: 'Observability' },
  { title: 'Warm-start recovery', body: 'On restart, degradation state is restored from Redis. If Redis is unavailable, starts at 50% concurrency and ramps to full over 60 seconds. No cold-start overload.', tag: 'Reliability' },
]

export const Features = forwardRef<HTMLElement>(function Features(_, ref) {
  return (
    <section id="features" ref={ref} style={{
      padding: 'clamp(60px, 8vw, 100px) clamp(16px, 4vw, 64px)',
      background: '#fff', borderBottom: '1px solid #e5e7eb',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ marginBottom: 56 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#1d4ed8', letterSpacing: '0.06em', textTransform: 'uppercase', margin: '0 0 10px' }}>Features</p>
          <h2 style={{ fontSize: 'clamp(26px, 3.5vw, 40px)', fontWeight: 800, color: '#111827', letterSpacing: '-0.02em', lineHeight: 1.2, margin: '0 0 14px', maxWidth: 560 }}>
            Built for real conditions
          </h2>
          <p style={{ fontSize: 16, color: '#6b7280', lineHeight: 1.6, margin: 0, maxWidth: 480 }}>
            Not a list of checkboxes. These are behaviors you can observe in the running system.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', border: '1px solid #e5e7eb', borderRadius: 10, overflow: 'hidden' }}>
          {features.map((f, i) => (
            <div key={f.title} style={{
              padding: '28px',
              background: '#fff',
              borderRight: (i % 2 === 0) ? '1px solid #e5e7eb' : 'none',
              borderBottom: i < 4 ? '1px solid #e5e7eb' : 'none',
              transition: 'background 150ms',
            }}
              onMouseEnter={e => (e.currentTarget.style.background = '#f9fafb')}
              onMouseLeave={e => (e.currentTarget.style.background = '#fff')}
            >
              <span style={{ fontSize: 11, fontWeight: 600, padding: '2px 8px', borderRadius: 4, background: '#f3f4f6', color: '#374151', display: 'inline-block', marginBottom: 12 }}>
                {f.tag}
              </span>
              <h3 style={{ fontSize: 15, fontWeight: 700, color: '#111827', margin: '0 0 8px', lineHeight: 1.3 }}>{f.title}</h3>
              <p style={{ fontSize: 13, color: '#6b7280', lineHeight: 1.7, margin: 0 }}>{f.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
})
