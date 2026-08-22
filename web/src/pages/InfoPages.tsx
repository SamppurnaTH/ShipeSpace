import { Link } from 'react-router-dom'

type InfoSection = {
  title: string
  body: string
}

type InfoPageProps = {
  eyebrow: string
  title: string
  intro: string
  sections: InfoSection[]
}

function InfoPage({ eyebrow, title, intro, sections }: InfoPageProps) {
  return (
    <div style={pageStyle}>
      <header style={headerStyle}>
        <p style={eyebrowStyle}>{eyebrow}</p>
        <h1 style={titleStyle}>{title}</h1>
        <p style={introStyle}>{intro}</p>
      </header>

      <section style={sectionGridStyle} aria-label={title}>
        {sections.map(section => (
          <article key={section.title} style={sectionStyle}>
            <h2 style={sectionTitleStyle}>{section.title}</h2>
            <p style={sectionBodyStyle}>{section.body}</p>
          </article>
        ))}
      </section>

      <footer style={footerStyle}>
        <Link to="/" style={linkStyle}>Back to KREVO</Link>
        <Link to="/help" style={linkStyle}>Help</Link>
        <Link to="/privacy" style={linkStyle}>Privacy</Link>
        <Link to="/terms" style={linkStyle}>Terms</Link>
      </footer>
    </div>
  )
}

export function HelpPage() {
  return (
    <InfoPage
      eyebrow="Support"
      title="Help Center"
      intro="Find the essentials for signing in, publishing ships, discovering builders, and managing your KREVO account during beta."
      sections={[
        {
          title: 'Signing in',
          body: 'KREVO uses GitHub OAuth so builders can start from an existing developer identity. If sign-in fails, confirm you are using the intended GitHub account and try the flow again.',
        },
        {
          title: 'Projects and ships',
          body: 'Create a project first, then publish ships as meaningful progress updates. Every ship belongs to a project timeline.',
        },
        {
          title: 'Collaboration',
          body: 'You can discover builders, follow their progress, comment on ships, and request collaboration on projects when available.',
        },
        {
          title: 'Beta support',
          body: 'KREVO is currently in beta readiness. If something looks broken, capture the page, action, and error message so the issue can be fixed quickly.',
        },
      ]}
    />
  )
}

export function PrivacyPage() {
  return (
    <InfoPage
      eyebrow="Privacy"
      title="Privacy Policy"
      intro="This beta privacy summary explains what KREVO uses to provide the builder experience. A fuller legal policy should replace this before broad public launch."
      sections={[
        {
          title: 'GitHub identity',
          body: 'KREVO uses GitHub OAuth to verify identity and create your builder profile. Basic profile information such as username, avatar, and email may be used for account functionality.',
        },
        {
          title: 'Public content',
          body: 'Projects, ships, comments, profile details, and collaboration signals may be visible to other users or public visitors depending on the page.',
        },
        {
          title: 'Operational data',
          body: 'The service may store logs, rate-limit records, audit events, and security metadata needed to keep the beta reliable and safe.',
        },
        {
          title: 'Data control',
          body: 'During beta, account and content removal requests should be handled by the project owner or administrator until self-service controls are expanded.',
        },
      ]}
    />
  )
}

export function TermsPage() {
  return (
    <InfoPage
      eyebrow="Terms"
      title="Terms of Use"
      intro="These beta terms set clear expectations for using KREVO while the product is still being validated with early users."
      sections={[
        {
          title: 'Use KREVO for real building',
          body: 'KREVO is intended for sharing legitimate technical project progress, discovering builders, and collaborating respectfully.',
        },
        {
          title: 'Respect other builders',
          body: 'Do not harass users, spam feeds, impersonate others, publish malicious content, or abuse engagement and collaboration features.',
        },
        {
          title: 'Your content',
          body: 'You are responsible for the projects, ships, comments, links, and media you publish. Only share content you have the right to share.',
        },
        {
          title: 'Beta availability',
          body: 'KREVO may change, pause, remove, or restrict features during beta as the product is stabilized and prepared for broader release.',
        },
      ]}
    />
  )
}

const pageStyle: React.CSSProperties = {
  maxWidth: 860,
  margin: '0 auto',
  padding: 'var(--space-10) var(--space-4) var(--space-12)',
}

const headerStyle: React.CSSProperties = {
  marginBottom: 'var(--space-8)',
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
  fontSize: 'var(--text-3xl)',
  fontWeight: 800,
}

const introStyle: React.CSSProperties = {
  maxWidth: 720,
  margin: 'var(--space-4) 0 0',
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-lg)',
  lineHeight: 1.6,
}

const sectionGridStyle: React.CSSProperties = {
  display: 'grid',
  gap: 'var(--space-4)',
}

const sectionStyle: React.CSSProperties = {
  padding: 'var(--space-5)',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-lg)',
  boxShadow: 'var(--shadow-sm)',
}

const sectionTitleStyle: React.CSSProperties = {
  margin: '0 0 var(--space-2)',
  color: 'var(--color-text-primary)',
  fontSize: 'var(--text-lg)',
  fontWeight: 700,
}

const sectionBodyStyle: React.CSSProperties = {
  margin: 0,
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-base)',
  lineHeight: 1.6,
}

const footerStyle: React.CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: 'var(--space-4)',
  marginTop: 'var(--space-8)',
}

const linkStyle: React.CSSProperties = {
  color: 'var(--color-brand)',
  fontWeight: 700,
  textDecoration: 'none',
}
