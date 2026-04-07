export function LandingFooter() {
  return (
    <footer style={{
      padding: '24px clamp(16px, 4vw, 64px)',
      borderTop: '1px solid #e5e7eb',
      background: '#f9fafb',
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      flexWrap: 'wrap', gap: 12,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 16 }}>⚓</span>
        <span style={{ fontSize: 13, fontWeight: 700, color: '#374151' }}>ShipSpace</span>
      </div>
      <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
        {[
          { label: 'GitHub', href: 'https://github.com/SamppurnaTH/ShipeSpace', external: true },
          { label: 'Open app', href: '/app' },
        ].map(({ label, href, external }) => (
          <a key={label} href={href}
            target={external ? '_blank' : undefined}
            rel={external ? 'noopener noreferrer' : undefined}
            style={{ fontSize: 13, color: '#6b7280', textDecoration: 'none' }}
            onMouseEnter={e => (e.currentTarget.style.color = '#111827')}
            onMouseLeave={e => (e.currentTarget.style.color = '#6b7280')}
          >{label}</a>
        ))}
      </div>
      <span style={{ fontSize: 12, color: '#9ca3af' }}>
        Spring Boot · React · MongoDB · Redis
      </span>
    </footer>
  )
}
