import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const pillars = [
  {
    title: 'Create',
    body: 'Give each project a public home with context, stack details, links, and a timeline.',
  },
  {
    title: 'Ship',
    body: 'Publish progress updates tied to real projects, not isolated status posts.',
  },
  {
    title: 'Discover',
    body: 'Find builders, projects, and technical ideas through public feeds and search.',
  },
  {
    title: 'Collaborate',
    body: 'Follow other builders, discuss ships, and request collaboration when a project is open to it.',
  },
  {
    title: 'Evolve',
    body: 'Build a profile that reflects visible project momentum through ShipScore and rank signals.',
  },
]

const audiences = ['Developers', 'Students', 'Indie hackers', 'Makers', 'Technical teams']

export function AboutPage() {
  const { user } = useAuth()

  useEffect(() => {
    document.title = 'About KREVO'
  }, [])

  return (
    <article style={pageStyle} aria-labelledby="about-title">
      <header style={heroStyle}>
        <p style={eyebrowStyle}>About KREVO</p>
        <h1 id="about-title" style={titleStyle}>Create. Ship. Evolve.</h1>
        <p style={introStyle}>
          KREVO is a builder network for people who turn ideas into real projects. It helps technical builders share progress, discover each other, and build a public project timeline through real work.
        </p>
        <div style={ctaRowStyle}>
          <Link to={user ? '/projects' : '/auth'} style={primaryLinkStyle}>
            {user ? 'Create a project' : 'Continue with GitHub'}
          </Link>
          <Link to="/trending" style={secondaryLinkStyle}>Explore ships</Link>
        </div>
      </header>

      <section style={sectionStyle} aria-labelledby="why-krevo">
        <p style={eyebrowStyle}>Why it exists</p>
        <h2 id="why-krevo" style={sectionHeadingStyle}>Your project timeline, not another resume.</h2>
        <p style={bodyStyle}>
          Most builder profiles show outcomes after the work is finished. KREVO is designed around the progress in between: the project decisions, releases, fixes, collaborations, and small wins that show how a builder actually moves.
        </p>
      </section>

      <section style={gridStyle} aria-label="How KREVO works">
        {pillars.map(item => (
          <article key={item.title} style={cardStyle}>
            <h2 style={cardTitleStyle}>{item.title}</h2>
            <p style={cardBodyStyle}>{item.body}</p>
          </article>
        ))}
      </section>

      <section style={sectionStyle} aria-labelledby="who-krevo-is-for">
        <p style={eyebrowStyle}>Who it is for</p>
        <h2 id="who-krevo-is-for" style={sectionHeadingStyle}>Builders who want progress to be visible.</h2>
        <p style={bodyStyle}>
          KREVO is for people creating technical projects and looking for a clearer way to share what they are building, learn from others, and find collaborators.
        </p>
        <div style={tagRowStyle}>
          {audiences.map(audience => <span key={audience} style={tagStyle}>{audience}</span>)}
        </div>
      </section>
    </article>
  )
}

const pageStyle: React.CSSProperties = {
  maxWidth: 960,
  margin: '0 auto',
  padding: 'var(--space-10) var(--space-4) var(--space-12)',
  textAlign: 'center',
}

const heroStyle: React.CSSProperties = {
  maxWidth: 820,
  margin: '0 auto var(--space-10)',
}

const eyebrowStyle: React.CSSProperties = {
  margin: '0 0 var(--space-2)',
  color: 'var(--color-brand)',
  fontSize: 'var(--text-xs)',
  fontWeight: 800,
  letterSpacing: '0.08em',
  textTransform: 'uppercase',
}

const titleStyle: React.CSSProperties = {
  margin: 0,
  color: 'var(--color-text-primary)',
  fontSize: 'clamp(2.25rem, 6vw, 4.4rem)',
  lineHeight: 1.02,
  fontWeight: 850,
}

const introStyle: React.CSSProperties = {
  maxWidth: 720,
  margin: 'var(--space-5) auto 0',
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-lg)',
  lineHeight: 1.65,
}

const ctaRowStyle: React.CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  justifyContent: 'center',
  gap: 'var(--space-3)',
  marginTop: 'var(--space-7)',
}

const primaryLinkStyle: React.CSSProperties = {
  display: 'inline-flex',
  minHeight: 48,
  alignItems: 'center',
  justifyContent: 'center',
  padding: '0 var(--space-6)',
  color: '#fff',
  background: 'var(--color-brand)',
  borderRadius: '999px',
  fontWeight: 800,
  textDecoration: 'none',
  boxShadow: 'var(--shadow-md)',
}

const secondaryLinkStyle: React.CSSProperties = {
  ...primaryLinkStyle,
  color: 'var(--color-text-primary)',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  boxShadow: 'none',
}

const sectionStyle: React.CSSProperties = {
  maxWidth: 820,
  margin: '0 auto var(--space-8)',
}

const sectionHeadingStyle: React.CSSProperties = {
  margin: 0,
  color: 'var(--color-text-primary)',
  fontSize: 'var(--text-2xl)',
  fontWeight: 800,
}

const bodyStyle: React.CSSProperties = {
  maxWidth: 720,
  margin: 'var(--space-4) auto 0',
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-base)',
  lineHeight: 1.7,
}

const gridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
  gap: 'var(--space-4)',
  marginBottom: 'var(--space-10)',
}

const cardStyle: React.CSSProperties = {
  minHeight: 160,
  padding: 'var(--space-5)',
  textAlign: 'left',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-lg)',
  boxShadow: 'var(--shadow-sm)',
}

const cardTitleStyle: React.CSSProperties = {
  margin: '0 0 var(--space-2)',
  color: 'var(--color-text-primary)',
  fontSize: 'var(--text-lg)',
  fontWeight: 800,
}

const cardBodyStyle: React.CSSProperties = {
  margin: 0,
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-base)',
  lineHeight: 1.6,
}

const tagRowStyle: React.CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  justifyContent: 'center',
  gap: 'var(--space-2)',
  marginTop: 'var(--space-5)',
}

const tagStyle: React.CSSProperties = {
  padding: '8px 12px',
  color: 'var(--color-text-primary)',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: '999px',
  fontWeight: 700,
}
