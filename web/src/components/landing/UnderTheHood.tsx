const levels = [
  { level: 'L0', name: 'Normal', rate: '1.00×', color: '#16a34a', bg: '#f0fdf4', border: '#bbf7d0' },
  { level: 'L1', name: 'Personalization off', rate: '0.70×', color: '#ca8a04', bg: '#fefce8', border: '#fde68a' },
  { level: 'L2', name: 'Behavioral writes off', rate: '0.50×', color: '#ea580c', bg: '#fff7ed', border: '#fed7aa' },
  { level: 'L3', name: 'Async reduced', rate: '0.50×', color: '#dc2626', bg: '#fef2f2', border: '#fecaca' },
  { level: 'L4', name: 'Load shedding active', rate: '0.33×', color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe' },
]

const codeSnippet = `@Scheduled(fixedDelay = 10_000)
public void evaluateSlos() {
  double feedP95 = p95ms(feedTimer);
  double errRate = errorRate();

  // Degrade on streak OR violation rate
  if (feedP95 > 300 && streak >= 3) {
    degrade(PERSONALIZATION_DISABLED);
  }
  if (errRate > 0.01) {
    degrade(LOAD_SHEDDING_ACTIVE);
  }

  // Recover only when both are healthy
  if (healthy >= 9 && violationRate < 0.10) {
    recover(); // one level at a time
  }
}`

export function UnderTheHood() {
  return (
    <section style={{
      padding: 'clamp(60px, 8vw, 100px) clamp(16px, 4vw, 64px)',
      background: '#f9fafb', borderBottom: '1px solid #e5e7eb',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ marginBottom: 56 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#1d4ed8', letterSpacing: '0.06em', textTransform: 'uppercase', margin: '0 0 10px' }}>
            Technical depth
          </p>
          <h2 style={{ fontSize: 'clamp(26px, 3.5vw, 40px)', fontWeight: 800, color: '#111827', letterSpacing: '-0.02em', lineHeight: 1.2, margin: '0 0 14px', maxWidth: 560 }}>
            The control loop, explained
          </h2>
          <p style={{ fontSize: 16, color: '#6b7280', lineHeight: 1.6, margin: 0, maxWidth: 520 }}>
            The SLO enforcement service is a closed-loop controller. It reads real metrics, makes decisions, and adjusts system behavior — without human intervention.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 24, alignItems: 'start' }}>
          {/* Degradation ladder */}
          <div>
            <h3 style={{ fontSize: 12, fontWeight: 700, color: '#374151', margin: '0 0 16px', letterSpacing: '0.02em', textTransform: 'uppercase' as const }}>
              Degradation ladder
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {levels.map(l => (
                <div key={l.level} style={{
                  display: 'flex', alignItems: 'center', gap: 12,
                  padding: '12px 16px',
                  background: l.bg, border: `1px solid ${l.border}`,
                  borderRadius: 8,
                }}>
                  <code style={{ fontSize: 11, fontWeight: 700, color: l.color, fontFamily: "'JetBrains Mono', monospace", minWidth: 24 }}>
                    {l.level}
                  </code>
                  <span style={{ fontSize: 13, fontWeight: 500, color: '#374151', flex: 1 }}>{l.name}</span>
                  <code style={{ fontSize: 12, fontWeight: 700, color: l.color, fontFamily: "'JetBrains Mono', monospace" }}>
                    {l.rate}
                  </code>
                </div>
              ))}
            </div>
            <p style={{ fontSize: 12, color: '#9ca3af', marginTop: 12, lineHeight: 1.5 }}>
              Rate multiplier applied to per-user limits at each level. Recovery requires 9 consecutive healthy cycles.
            </p>
          </div>

          {/* Code block */}
          <div style={{ background: '#1e293b', borderRadius: 10, overflow: 'hidden', border: '1px solid #334155' }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 6,
              padding: '10px 16px', borderBottom: '1px solid #334155',
              background: '#0f172a',
            }}>
              {['#ff5f57','#febc2e','#28c840'].map(c => (
                <div key={c} style={{ width: 10, height: 10, borderRadius: '50%', background: c }} />
              ))}
              <span style={{ marginLeft: 8, fontSize: 11, color: '#64748b', fontFamily: "'JetBrains Mono', monospace" }}>
                SloEnforcementService.java
              </span>
            </div>
            <pre style={{
              margin: 0, padding: '20px',
              fontSize: 12, lineHeight: 1.7,
              color: '#e2e8f0',
              fontFamily: "'JetBrains Mono', monospace",
              overflowX: 'auto',
            }}>
              <code dangerouslySetInnerHTML={{ __html: codeSnippet
                .replace(/\/\/.*/g, m => `<span style="color:#64748b">${m}</span>`)
                .replace(/\b(double|void|public|if|return)\b/g, m => `<span style="color:#c084fc">${m}</span>`)
                .replace(/\b(feedP95|errRate|streak|healthy|violationRate)\b/g, m => `<span style="color:#93c5fd">${m}</span>`)
                .replace(/\b(degrade|recover)\b/g, m => `<span style="color:#6ee7b7">${m}</span>`)
                .replace(/\b(PERSONALIZATION_DISABLED|LOAD_SHEDDING_ACTIVE)\b/g, m => `<span style="color:#fbbf24">${m}</span>`)
              }} />
            </pre>
          </div>
        </div>

        <div style={{ marginTop: 24, padding: '16px 20px', background: '#fff', border: '1px solid #e5e7eb', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <p style={{ fontSize: 14, color: '#374151', margin: 0 }}>
            The full source is on GitHub. Every decision is documented with the reasoning behind it.
          </p>
          <a href="https://github.com/SamppurnaTH/ShipeSpace" target="_blank" rel="noopener noreferrer"
            style={{ fontSize: 14, fontWeight: 600, color: '#1d4ed8', textDecoration: 'none', whiteSpace: 'nowrap' }}
            onMouseEnter={e => (e.currentTarget.style.textDecoration = 'underline')}
            onMouseLeave={e => (e.currentTarget.style.textDecoration = 'none')}
          >
            View on GitHub →
          </a>
        </div>
      </div>
    </section>
  )
}
