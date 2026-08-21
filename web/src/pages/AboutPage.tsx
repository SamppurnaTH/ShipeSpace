import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function AboutPage() {
  const { user } = useAuth()

  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: 'var(--space-10) var(--space-4)' }}>
      <header style={{ textAlign: 'center', marginBottom: 'var(--space-12)' }}>
        <h1 style={{ fontSize: 'var(--text-3xl)', fontWeight: 800, letterSpacing: '-0.02em', marginBottom: 'var(--space-2)' }}>
          KREVO
        </h1>
        <p style={{ fontSize: 'var(--text-xl)', color: 'var(--color-brand)', fontWeight: 600 }}>
          Create. Ship. Evolve.
        </p>
        <p style={{ fontSize: 'var(--text-lg)', color: 'var(--color-text-secondary)', marginTop: 'var(--space-4)', maxWidth: 600, margin: 'var(--space-4) auto 0' }}>
          KREVO is a builder network for people who turn ideas into real projects.
        </p>
      </header>

      <section style={{ display: 'grid', gap: 'var(--space-8)', marginBottom: 'var(--space-12)' }}>
        <div style={stepStyle}>
          <h2 style={stepTitleStyle}>Create</h2>
          <p style={stepDescStyle}>Start a project and establish what you're building.</p>
        </div>
        <div style={stepStyle}>
          <h2 style={stepTitleStyle}>Ship</h2>
          <p style={stepDescStyle}>Share meaningful progress publicly.</p>
        </div>
        <div style={stepStyle}>
          <h2 style={stepTitleStyle}>Discover</h2>
          <p style={stepDescStyle}>Find builders, projects, and ideas.</p>
        </div>
        <div style={stepStyle}>
          <h2 style={stepTitleStyle}>Collaborate</h2>
          <p style={stepDescStyle}>Connect with people working on interesting things.</p>
        </div>
        <div style={stepStyle}>
          <h2 style={stepTitleStyle}>Evolve</h2>
          <p style={stepDescStyle}>Build experience, reputation, and momentum through real work.</p>
        </div>
      </section>

      <div style={{ textAlign: 'center' }}>
        <Link 
          to={user ? "/projects" : "/auth"} 
          style={{ 
            display: 'inline-block', 
            padding: '16px 32px', 
            background: 'var(--color-brand)', 
            color: '#fff', 
            borderRadius: 'var(--radius-lg)',
            fontSize: 'var(--text-lg)',
            fontWeight: 700,
            textDecoration: 'none'
          }}
        >
          {user ? 'Start building' : 'Continue with GitHub'}
        </Link>
      </div>
    </div>
  )
}

const stepStyle: React.CSSProperties = {
  padding: 'var(--space-6)',
  background: 'var(--color-surface)',
  borderRadius: 'var(--radius-xl)',
  border: '1px solid var(--color-border)',
}

const stepTitleStyle: React.CSSProperties = {
  fontSize: 'var(--text-xl)',
  fontWeight: 700,
  color: 'var(--color-text-primary)',
  marginBottom: 'var(--space-2)'
}

const stepDescStyle: React.CSSProperties = {
  fontSize: 'var(--text-base)',
  color: 'var(--color-text-secondary)',
  lineHeight: 1.5
}
