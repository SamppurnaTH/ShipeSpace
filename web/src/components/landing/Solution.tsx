const steps = [
  {
    step: '01',
    title: 'Adaptive feed, not a static query',
    body: 'The feed operates in three modes: personalized (ranked by your behavior), trending (engagement-weighted), and fallback (safe static ranking). The system switches modes automatically based on real-time health metrics — no manual intervention, no downtime.',
    tag: 'Smart degradation',
    detail: 'X-Feed-Mode header on every response',
  },
  {
    step: '02',
    title: 'Rate limiting tied to system state',
    body: 'Per-user rate limits are enforced at the service layer. The multiplier adjusts dynamically: 1.0x at normal, 0.70x when personalization is disabled, down to 0.33x when load shedding is active. Clients receive their remaining quota on every response.',
    tag: 'Adaptive rate limiting',
    detail: 'X-RateLimit-Remaining on every response',
  },
  {
    step: '03',
    title: 'The frontend knows what the backend is doing',
    body: 'Every response carries system state: feed mode, rate limit remaining, SLO degradation level. The built-in debug panel surfaces this in real time — filterable by errors or slow requests. No log diving, no guessing.',
    tag: 'Live observability',
    detail: 'Built-in debug panel, zero config',
  },
]

export function Solution() {
  return (
    <section id="how-it-works" style={{
      padding: 'clamp(60px, 8vw, 100px) clamp(16px, 4vw, 64px)',
      background: '#f9fafb',
      borderBottom: '1px solid #e5e7eb',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ marginBottom: 56 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#1d4ed8', letterSpacing: '0.06em', textTransform: 'uppercase', margin: '0 0 10px' }}>
            How it works
          </p>
          <h2 style={{ fontSize: 'clamp(26px, 3.5vw, 40px)', fontWeight: 800, color: '#111827', letterSpacing: '-0.02em', lineHeight: 1.2, margin: '0 0 14px', maxWidth: 560 }}>
            A backend that adapts instead of failing
          </h2>
          <p style={{ fontSize: 16, color: '#6b7280', lineHeight: 1.6, margin: 0, maxWidth: 520 }}>
            ShipSpace runs a closed-loop control system that monitors its own health and adjusts behavior in real time.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {steps.map(s => (
            <div key={s.step} style={{
              display: 'grid',
              gridTemplateColumns: '56px 1fr',
              gap: '0 32px',
              padding: '28px 32px',
              background: '#fff',
              border: '1px solid #e5e7eb',
              borderRadius: 10,
              alignItems: 'start',
            }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: '#d1d5db', fontFamily: "'JetBrains Mono', monospace", paddingTop: 4 }}>
                {s.step}
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10, flexWrap: 'wrap' }}>
                  <h3 style={{ fontSize: 17, fontWeight: 700, color: '#111827', margin: 0, lineHeight: 1.3 }}>
                    {s.title}
                  </h3>
                  <span style={{
                    fontSize: 11, fontWeight: 600, letterSpacing: '0.04em',
                    padding: '2px 8px', borderRadius: 4,
                    background: '#eff6ff', color: '#1d4ed8',
                    border: '1px solid #bfdbfe',
                  }}>
                    {s.tag}
                  </span>
                </div>
                <p style={{ fontSize: 14, color: '#6b7280', lineHeight: 1.7, margin: '0 0 12px' }}>
                  {s.body}
                </p>
                <code style={{
                  fontSize: 12, color: '#374151',
                  background: '#f3f4f6', padding: '3px 8px', borderRadius: 4,
                  fontFamily: "'JetBrains Mono', monospace",
                }}>
                  {s.detail}
                </code>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
