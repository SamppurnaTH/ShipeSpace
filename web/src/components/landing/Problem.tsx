const problems = [
  {
    number: '01',
    title: 'Feeds break under load',
    body: 'Most platforms run the same database query for every user. When traffic spikes, the feed slows, errors out, or returns stale data — with no indication to the user that anything is wrong.',
    contrast: 'ShipSpace switches feed modes automatically based on system health.',
  },
  {
    number: '02',
    title: 'Failures cascade silently',
    body: 'When a cache misses, a DB query times out, or an external API is slow — the whole request fails. Users see a generic 500. Engineers see nothing useful until they dig through logs.',
    contrast: 'ShipSpace degrades progressively. Each failure mode has a defined response.',
  },
  {
    number: '03',
    title: 'The frontend is blind',
    body: 'The UI has no idea what the backend is doing. Is the feed personalized or fallback? Is rate limiting active? Is the system under load? There\'s no way to know without reading server logs.',
    contrast: 'Every ShipSpace response carries system state headers the UI can act on.',
  },
]

export function Problem() {
  return (
    <section style={{
      padding: 'clamp(60px, 8vw, 100px) clamp(16px, 4vw, 64px)',
      background: '#fff',
      borderBottom: '1px solid #e5e7eb',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        {/* Section header — left aligned */}
        <div style={{ marginBottom: 56 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#1d4ed8', letterSpacing: '0.06em', textTransform: 'uppercase', margin: '0 0 10px' }}>
            The problem
          </p>
          <h2 style={{ fontSize: 'clamp(26px, 3.5vw, 40px)', fontWeight: 800, color: '#111827', letterSpacing: '-0.02em', lineHeight: 1.2, margin: '0 0 14px', maxWidth: 560 }}>
            Most apps aren't built for real conditions
          </h2>
          <p style={{ fontSize: 16, color: '#6b7280', lineHeight: 1.6, margin: 0, maxWidth: 480 }}>
            They work in development. They fall apart when real users show up.
          </p>
        </div>

        {/* Problem rows */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {problems.map((p, i) => (
            <div key={p.number} style={{
              display: 'grid',
              gridTemplateColumns: '56px 1fr 1fr',
              gap: '0 40px',
              padding: '32px 0',
              borderTop: i === 0 ? '1px solid #e5e7eb' : '1px solid #f3f4f6',
              alignItems: 'start',
            }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: '#d1d5db', fontFamily: "'JetBrains Mono', monospace", paddingTop: 3 }}>
                {p.number}
              </div>
              <div>
                <h3 style={{ fontSize: 17, fontWeight: 700, color: '#111827', margin: '0 0 10px', lineHeight: 1.3 }}>
                  {p.title}
                </h3>
                <p style={{ fontSize: 14, color: '#6b7280', lineHeight: 1.7, margin: 0 }}>
                  {p.body}
                </p>
              </div>
              <div style={{
                padding: '14px 16px',
                background: '#eff6ff',
                border: '1px solid #bfdbfe',
                borderRadius: 8,
                fontSize: 13, color: '#1e40af', lineHeight: 1.6,
                fontWeight: 500,
              }}>
                ✓ {p.contrast}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
