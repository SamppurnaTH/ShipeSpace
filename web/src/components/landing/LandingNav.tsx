export function LandingNav() {
  return (
    <nav style={{
      position: 'fixed', top: 0, left: 0, right: 0, zIndex: 200,
      background: '#fff',
      borderBottom: '1px solid #e5e7eb',
      height: 64,
      display: 'flex', alignItems: 'center',
      padding: '0 clamp(16px, 4vw, 64px)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', maxWidth: 1200, margin: '0 auto' }}>
        {/* Logo */}
        <a href="/" style={{ display: 'flex', alignItems: 'center', gap: 8, textDecoration: 'none' }}>
          <span style={{ fontSize: 22 }}>⚓</span>
          <span style={{ fontSize: 17, fontWeight: 700, color: '#111827', letterSpacing: '-0.3px' }}>ShipSpace</span>
        </a>

        {/* Desktop nav */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          {[
            { label: 'Features', href: '#features' },
            { label: 'How it works', href: '#how-it-works' },
            { label: 'GitHub', href: 'https://github.com/SamppurnaTH/ShipeSpace', external: true },
          ].map(({ label, href, external }) => (
            <a key={label} href={href}
              target={external ? '_blank' : undefined}
              rel={external ? 'noopener noreferrer' : undefined}
              style={{ padding: '6px 14px', fontSize: 14, fontWeight: 500, color: '#374151', textDecoration: 'none', borderRadius: 6 }}
              onMouseEnter={e => { e.currentTarget.style.background = '#f3f4f6'; e.currentTarget.style.color = '#111827' }}
              onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#374151' }}
            >{label}</a>
          ))}
          <a href="/app" style={{
            marginLeft: 8, padding: '8px 20px', borderRadius: 6,
            fontSize: 14, fontWeight: 600,
            background: '#1d4ed8', color: '#fff', textDecoration: 'none',
            transition: 'background 150ms',
          }}
            onMouseEnter={e => (e.currentTarget.style.background = '#1e40af')}
            onMouseLeave={e => (e.currentTarget.style.background = '#1d4ed8')}
          >
            Open app
          </a>
        </div>
      </div>
    </nav>
  )
}
