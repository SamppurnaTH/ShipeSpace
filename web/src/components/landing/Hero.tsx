interface HeroProps { onLearnMore: () => void }

export function Hero({ onLearnMore }: HeroProps) {
  return (
    <section style={{
      paddingTop: 'clamp(120px, 16vw, 180px)',
      paddingBottom: 'clamp(80px, 10vw, 120px)',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      color: '#fff',
      position: 'relative',
      overflow: 'hidden',
    }}>
      {/* Background pattern */}
      <div style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundImage: `url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.05'%3E%3Ccircle cx='30' cy='30' r='2'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")`,
        opacity: 0.1,
      }} />

      <div style={{
        maxWidth: 1200,
        margin: '0 auto',
        padding: '0 clamp(16px, 4vw, 64px)',
        position: 'relative',
        zIndex: 1,
        display: 'grid',
        gridTemplateColumns: '1fr auto',
        gap: 64,
        alignItems: 'center',
      }}>
        {/* Left: copy */}
        <div style={{ maxWidth: 640 }}>
          {/* Eyebrow */}
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '6px 16px',
            borderRadius: 20,
            background: 'rgba(255,255,255,0.15)',
            backdropFilter: 'blur(10px)',
            border: '1px solid rgba(255,255,255,0.2)',
            fontSize: 14,
            fontWeight: 600,
            color: '#fff',
            letterSpacing: '0.05em',
            textTransform: 'uppercase',
            marginBottom: 32,
            boxShadow: '0 4px 20px rgba(0,0,0,0.1)',
          }}>
            <span style={{ fontSize: 16 }}>⚓</span>
            Open Source · Production-Grade
          </div>

          <h1 style={{
            fontSize: 'clamp(40px, 6vw, 64px)',
            fontWeight: 900,
            lineHeight: 1.05,
            letterSpacing: '-0.04em',
            color: '#fff',
            margin: '0 0 24px',
            textShadow: '0 2px 10px rgba(0,0,0,0.3)',
          }}>
            Ship Smarter,<br />
            Scale Safer
          </h1>

          <p style={{
            fontSize: 'clamp(18px, 2vw, 20px)',
            lineHeight: 1.6,
            color: 'rgba(255,255,255,0.9)',
            margin: '0 0 40px',
            maxWidth: 540,
            textShadow: '0 1px 5px rgba(0,0,0,0.2)',
          }}>
            A developer platform that adapts under pressure. Built for real-world chaos,
            with graceful degradation, intelligent rate limiting, and transparent observability.
          </p>

          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <a href="/auth" style={{
              padding: '16px 32px',
              borderRadius: 8,
              fontSize: 16,
              fontWeight: 600,
              background: '#fff',
              color: '#667eea',
              textDecoration: 'none',
              boxShadow: '0 8px 25px rgba(0,0,0,0.15)',
              transition: 'all 200ms ease',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
            }}
              onMouseEnter={e => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 12px 35px rgba(0,0,0,0.2)';
              }}
              onMouseLeave={e => {
                e.currentTarget.style.transform = 'none';
                e.currentTarget.style.boxShadow = '0 8px 25px rgba(0,0,0,0.15)';
              }}
            >
              Try the Live Demo
              <span style={{ fontSize: 18 }}>→</span>
            </a>
            <button onClick={onLearnMore} style={{
              padding: '16px 32px',
              borderRadius: 8,
              fontSize: 16,
              fontWeight: 500,
              background: 'transparent',
              color: '#fff',
              border: '2px solid rgba(255,255,255,0.3)',
              cursor: 'pointer',
              fontFamily: 'inherit',
              transition: 'all 200ms ease',
              backdropFilter: 'blur(10px)',
            }}
              onMouseEnter={e => {
                e.currentTarget.style.background = 'rgba(255,255,255,0.1)';
                e.currentTarget.style.borderColor = 'rgba(255,255,255,0.5)';
              }}
              onMouseLeave={e => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.borderColor = 'rgba(255,255,255,0.3)';
              }}
            >
              Learn How It Works
            </button>
          </div>
        </div>

        {/* Right: stat block */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 16,
          minWidth: 280,
        }}>
          {[
            { value: '5', label: 'Degradation Levels', sub: 'Auto-Managed', icon: '⚡' },
            { value: '10s', label: 'SLO Monitoring', sub: 'Real-Time', icon: '📊' },
            { value: '500+', label: 'Chaos Threads', sub: 'Tested', icon: '🔥' },
            { value: '0', label: 'Race Conditions', sub: 'Guaranteed', icon: '🛡️' },
          ].map(s => (
            <div key={s.label} style={{
              padding: '24px 20px',
              background: 'rgba(255,255,255,0.1)',
              backdropFilter: 'blur(10px)',
              border: '1px solid rgba(255,255,255,0.2)',
              borderRadius: 12,
              textAlign: 'center',
              transition: 'all 200ms ease',
              boxShadow: '0 4px 15px rgba(0,0,0,0.1)',
            }}
              onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-2px)'}
              onMouseLeave={e => e.currentTarget.style.transform = 'none'}
            >
              <div style={{
                fontSize: 32,
                fontWeight: 900,
                color: '#fff',
                letterSpacing: '-0.04em',
                lineHeight: 1,
                marginBottom: 8,
                textShadow: '0 2px 10px rgba(0,0,0,0.3)',
              }}>
                {s.icon} {s.value}
              </div>
              <div style={{
                fontSize: 14,
                fontWeight: 700,
                color: '#fff',
                marginBottom: 4,
                textShadow: '0 1px 5px rgba(0,0,0,0.2)',
              }}>
                {s.label}
              </div>
              <div style={{
                fontSize: 12,
                color: 'rgba(255,255,255,0.8)',
                textShadow: '0 1px 3px rgba(0,0,0,0.2)',
              }}>
                {s.sub}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
