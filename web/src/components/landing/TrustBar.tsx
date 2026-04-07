const stack = [
  { name: 'Spring Boot', role: 'Backend framework' },
  { name: 'MongoDB', role: 'Primary datastore' },
  { name: 'Redis', role: 'Cache + state' },
  { name: 'Micrometer', role: 'Metrics + SLO' },
  { name: 'React 19', role: 'Frontend' },
  { name: 'Docker', role: 'Containerized' },
]

export function TrustBar() {
  return (
    <div style={{
      borderTop: '1px solid #e5e7eb',
      borderBottom: '1px solid #e5e7eb',
      background: '#f9fafb',
      padding: '20px clamp(16px, 4vw, 64px)',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: '#9ca3af', letterSpacing: '0.06em', textTransform: 'uppercase', marginRight: 8, whiteSpace: 'nowrap' }}>
          Built with
        </span>
        {stack.map(s => (
          <div key={s.name} style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '5px 12px',
            background: '#fff', border: '1px solid #e5e7eb',
            borderRadius: 6,
          }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{s.name}</span>
            <span style={{ fontSize: 11, color: '#9ca3af' }}>{s.role}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
