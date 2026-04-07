export function FinalCTA() {
  return (
    <section style={{
      padding: 'clamp(60px, 8vw, 100px) clamp(16px, 4vw, 64px)',
      background: '#1d4ed8',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto', display: 'grid', gridTemplateColumns: '1fr auto', gap: 40, alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ fontSize: 'clamp(24px, 3.5vw, 38px)', fontWeight: 800, color: '#fff', letterSpacing: '-0.02em', lineHeight: 1.2, margin: '0 0 12px' }}>
            See it running. Not just described.
          </h2>
          <p style={{ fontSize: 16, color: 'rgba(255,255,255,0.75)', lineHeight: 1.6, margin: 0, maxWidth: 520 }}>
            The demo is live with real data, real rate limiting, and a real adaptive backend.
            Open the debug panel and watch the system respond.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', flexShrink: 0 }}>
          <a href="/app" style={{
            padding: '13px 28px', borderRadius: 6,
            fontSize: 15, fontWeight: 600,
            background: '#fff', color: '#1d4ed8',
            textDecoration: 'none',
            boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
            transition: 'all 150ms',
            whiteSpace: 'nowrap',
          }}
            onMouseEnter={e => { e.currentTarget.style.background = '#f0f9ff'; e.currentTarget.style.transform = 'translateY(-1px)' }}
            onMouseLeave={e => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.transform = 'none' }}
          >
            Open the app →
          </a>
          <a href="https://github.com/SamppurnaTH/ShipeSpace" target="_blank" rel="noopener noreferrer"
            style={{
              padding: '13px 28px', borderRadius: 6,
              fontSize: 15, fontWeight: 500,
              background: 'rgba(255,255,255,0.12)',
              border: '1px solid rgba(255,255,255,0.25)',
              color: '#fff', textDecoration: 'none',
              transition: 'all 150ms', whiteSpace: 'nowrap',
            }}
            onMouseEnter={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.2)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.12)')}
          >
            Read the source
          </a>
        </div>
      </div>
    </section>
  )
}
