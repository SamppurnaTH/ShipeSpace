import { useEffect } from 'react'
import { Link } from 'react-router-dom'

type InfoSection = {
  title: string
  body?: string
  items?: string[]
}

type InfoPageProps = {
  eyebrow: string
  title: string
  description: string
  lastUpdated?: string
  sections: InfoSection[]
}

const UPDATED = 'August 22, 2026'

function InfoPage({ eyebrow, title, description, lastUpdated, sections }: InfoPageProps) {
  useEffect(() => {
    document.title = title
  }, [title])

  return (
    <article style={pageStyle} aria-labelledby="info-title">
      <header style={headerStyle}>
        <p style={eyebrowStyle}>{eyebrow}</p>
        <h1 id="info-title" style={titleStyle}>{title}</h1>
        <p style={introStyle}>{description}</p>
        {lastUpdated && <p style={dateStyle}>Last updated: {lastUpdated}</p>}
      </header>

      <nav aria-label={`${title} sections`} style={tocStyle}>
        {sections.map(section => (
          <a key={section.title} href={`#${slug(section.title)}`} style={tocLinkStyle}>
            {section.title}
          </a>
        ))}
      </nav>

      <section style={sectionGridStyle}>
        {sections.map(section => (
          <section key={section.title} id={slug(section.title)} style={sectionStyle} aria-labelledby={`${slug(section.title)}-title`}>
            <h2 id={`${slug(section.title)}-title`} style={sectionTitleStyle}>{section.title}</h2>
            {section.body && <p style={sectionBodyStyle}>{section.body}</p>}
            {section.items && (
              <ul style={listStyle}>
                {section.items.map(item => <li key={item}>{item}</li>)}
              </ul>
            )}
          </section>
        ))}
      </section>

      <footer style={footerStyle}>
        <Link to="/" style={linkStyle}>KREVO home</Link>
        <Link to="/about" style={linkStyle}>About</Link>
        <Link to="/help" style={linkStyle}>Help</Link>
        <Link to="/privacy" style={linkStyle}>Privacy</Link>
        <Link to="/terms" style={linkStyle}>Terms</Link>
      </footer>
    </article>
  )
}

export function HelpPage() {
  return (
    <InfoPage
      eyebrow="Help"
      title="KREVO Help Center"
      description="Practical guidance for signing in, creating projects, publishing ships, discovering builders, collaborating, and understanding account states during beta."
      sections={[
        {
          title: 'Getting started',
          items: [
            'Sign in with GitHub, then KREVO creates or resolves your builder account.',
            'Review your profile details, then create your first project from the Projects area.',
            'Publish Ships as progress updates connected to a project.',
            'Use feeds, search, and People to discover builders and public work.',
          ],
        },
        {
          title: 'GitHub login',
          body: 'GitHub is the supported authentication method for the current beta. If login fails or is cancelled, return to the sign-in page and start the GitHub flow again. KREVO does not ask for or store a GitHub password.',
        },
        {
          title: 'Projects and ships',
          body: 'A Project represents what you are building. A Ship is a progress update linked to a Project. Ships can include text, links, and supported media, and may be discoverable in public feeds.',
        },
        {
          title: 'Discovery and engagement',
          body: 'You can browse public ships, search builders and projects, follow builders, like ships, comment, and use the following feed after signing in.',
        },
        {
          title: 'Collaboration',
          body: 'KREVO supports collaboration requests with incoming and sent views. A request can be accepted or declined by the project owner; KREVO does not guarantee that a collaboration will lead to work, payment, or delivery.',
        },
        {
          title: 'Notifications',
          body: 'In-app notifications help you find follow, like, comment, and collaboration activity. Email and real-time push notifications are not part of the current beta scope.',
        },
        {
          title: 'ShipScore and rank',
          body: 'ShipScore is KREVO\'s platform reputation signal based on selected meaningful activity inside KREVO. It is not a certification, employment credential, or objective measure of professional ability.',
        },
        {
          title: 'Restricted accounts',
          body: 'Some accounts may be restricted from write actions such as publishing, liking, commenting, following, or uploading. Restricted users may still browse public content and log out.',
        },
        {
          title: 'Troubleshooting',
          items: [
            'If a feed does not load, refresh the page and try again after a moment.',
            'If media upload is rejected, check file type, size, and whether the file is a valid image.',
            'If your session expires, sign in again with GitHub.',
            'Support contact information will be published through official KREVO channels before broader launch.',
          ],
        },
      ]}
    />
  )
}

export function PrivacyPage() {
  return (
    <InfoPage
      eyebrow="Privacy"
      title="KREVO Privacy Policy"
      description="This beta privacy summary explains how KREVO currently uses information to operate the builder network. It is written for transparency during beta and should receive professional legal review before broad commercial or public launch."
      lastUpdated={UPDATED}
      sections={[
        {
          title: 'Overview',
          body: 'KREVO uses information to provide GitHub login, builder profiles, projects, ships, discovery, collaboration, notifications, reputation signals, moderation, and basic service reliability.',
        },
        {
          title: 'Information from GitHub',
          body: 'When you sign in, KREVO requests GitHub OAuth access with read:user and user:email scopes. KREVO may receive your GitHub account identifier, username, avatar, profile fields, and email addresses made available by GitHub. KREVO does not receive your GitHub password.',
        },
        {
          title: 'Information you provide',
          items: [
            'Profile details such as bio, location, and website URL.',
            'Project names, descriptions, technology tags, and external links.',
            'Ships, comments, collaboration requests, and uploaded media references.',
            'Engagement actions such as likes, follows, comment upvotes, and notification read state.',
          ],
        },
        {
          title: 'Public information',
          body: 'KREVO is built for public project progress. Profiles, projects, ships, comments, ShipScore, rank, follower/following counts, and search/feed appearances may be visible to other users or public visitors depending on the page. Do not publish confidential information in public areas.',
        },
        {
          title: 'Tokens and local storage',
          body: 'The current web app stores KREVO-issued access and refresh tokens in browser localStorage. It also uses sessionStorage to remember the safe return path during GitHub login. Logout clears local tokens and asks the API to revoke current tokens.',
        },
        {
          title: 'Operational and security data',
          body: 'KREVO may process request metadata, rate-limit records, token blacklist entries, OAuth state records, audit logs, and abuse-prevention signals needed to operate and protect the beta service.',
        },
        {
          title: 'Personalization and reputation data',
          body: 'KREVO stores selected behavioral and scoring records to power discovery, personalization, ShipScore, and rank features. These records are product signals, not third-party advertising profiles.',
        },
        {
          title: 'Retention',
          items: [
            'OAuth state records are short-lived and currently expire after about 10 minutes.',
            'Notifications, behavioral signals, and ShipScore event logs currently expire after about 90 days.',
            'Admin audit logs currently expire after about 365 days.',
            'Account, profile, project, ship, comment, follow, collaboration, and media-reference retention is not yet governed by a finalized public deletion policy.',
          ],
        },
        {
          title: 'Service providers',
          body: 'KREVO uses GitHub for authentication and may use hosting, database, cache, and media-storage providers to run the service. Provider details may change as the beta infrastructure evolves.',
        },
        {
          title: 'Analytics and advertising',
          body: 'The current codebase does not include Google Analytics, Mixpanel, Segment, PostHog, Meta Pixel, Hotjar, or an advertising system. KREVO may still use internal logs and product records to operate the service.',
        },
        {
          title: 'Your choices',
          body: 'You can choose what you publish publicly and can log out from your session. A self-service account deletion workflow is not currently available in the beta; deletion and privacy-request handling require a defined operating process before broad launch.',
        },
        {
          title: 'Security',
          body: 'KREVO uses technical measures intended to protect the service, but no system can guarantee absolute security. Users should avoid posting secrets, credentials, private keys, or confidential project material.',
        },
        {
          title: 'Children and legal review',
          body: 'KREVO is not intentionally designed for young children. Age requirements, privacy rights by jurisdiction, international data handling, legal entity details, and contact channels require qualified legal review before broad public launch.',
        },
      ]}
    />
  )
}

export function TermsPage() {
  return (
    <InfoPage
      eyebrow="Terms"
      title="KREVO Terms of Use"
      description="These beta terms explain the basic expectations for using KREVO while the product is being validated. They are practical product terms, not a substitute for professional legal review before commercial launch."
      lastUpdated={UPDATED}
      sections={[
        {
          title: 'Acceptance',
          body: 'By using KREVO, you agree to use the service responsibly and follow these beta terms. If you do not agree, do not use the service.',
        },
        {
          title: 'Using KREVO',
          body: 'KREVO is intended for sharing legitimate technical project progress, discovering builders, engaging with public ships, and requesting collaboration respectfully.',
        },
        {
          title: 'Accounts and GitHub',
          body: 'GitHub OAuth is the supported account method for this beta. You are responsible for your GitHub account and KREVO session. GitHub is a third-party authentication provider and does not endorse KREVO.',
        },
        {
          title: 'User content',
          body: 'You are responsible for projects, ships, comments, links, screenshots, uploaded media, and other content you publish. Users retain ownership of content they create, subject to the permissions KREVO needs to host, display, process, and operate that content through the service. Exact commercial content-license language requires legal review.',
        },
        {
          title: 'Public project progress',
          body: 'KREVO is designed around public building. Content you publish may be visible in profiles, feeds, search, project pages, and ship pages. Do not post confidential information, private credentials, or content you do not have rights to share.',
        },
        {
          title: 'Acceptable use',
          items: [
            'Do not harass, threaten, impersonate, spam, or abuse other builders.',
            'Do not upload malicious files or attempt unauthorized access.',
            'Do not manipulate ShipScore, rankings, feeds, follows, likes, comments, or collaboration flows.',
            'Do not use KREVO to publish unlawful, deceptive, or rights-infringing content.',
          ],
        },
        {
          title: 'Collaboration',
          body: 'Collaboration requests are user-to-user interactions. KREVO is not an employer, recruiter, contractor, payment provider, or guarantor of collaboration outcomes.',
        },
        {
          title: 'ShipScore and rank',
          body: 'ShipScore and rank are KREVO platform features designed to reflect selected activity and contribution signals within KREVO. Their rules may evolve as the product develops. They do not verify professional skill, guarantee employment eligibility, or certify competence.',
        },
        {
          title: 'Moderation and restrictions',
          body: 'KREVO may restrict accounts or write actions when needed to protect the service and community. Restricted users may still be able to browse public content and log out. A formal public appeal process is not yet defined for beta.',
        },
        {
          title: 'Service availability',
          body: 'KREVO is a beta product. Features may change, break, be interrupted, or be removed as the service evolves. KREVO does not promise continuous availability, guaranteed uptime, or zero data loss.',
        },
        {
          title: 'Third-party services',
          body: 'KREVO depends on third-party services such as GitHub authentication and infrastructure providers. Their own terms and policies may apply when you interact with them.',
        },
        {
          title: 'No paid features in beta',
          body: 'The current MVP does not include paid subscriptions, billing, renewals, or refunds. Payment terms should be added only if monetization is introduced.',
        },
        {
          title: 'Legal review needed',
          body: 'Governing law, dispute resolution, liability limitations, content licensing details, privacy rights, age requirements, legal entity name, and official contact channels require qualified legal review before broad commercial or public launch.',
        },
      ]}
    />
  )
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')
}

const pageStyle: React.CSSProperties = {
  maxWidth: 980,
  margin: '0 auto',
  padding: 'var(--space-10) var(--space-4) var(--space-12)',
  textAlign: 'center',
}

const headerStyle: React.CSSProperties = {
  maxWidth: 780,
  margin: '0 auto var(--space-7)',
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
  fontSize: 'clamp(2rem, 5vw, 3.4rem)',
  lineHeight: 1.08,
  fontWeight: 850,
}

const introStyle: React.CSSProperties = {
  maxWidth: 760,
  margin: 'var(--space-4) auto 0',
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-lg)',
  lineHeight: 1.65,
}

const dateStyle: React.CSSProperties = {
  margin: 'var(--space-3) 0 0',
  color: 'var(--color-text-muted)',
  fontSize: 'var(--text-sm)',
  fontWeight: 700,
}

const tocStyle: React.CSSProperties = {
  maxWidth: 820,
  margin: '0 auto var(--space-6)',
  display: 'flex',
  flexWrap: 'wrap',
  justifyContent: 'center',
  gap: 'var(--space-2)',
}

const tocLinkStyle: React.CSSProperties = {
  padding: '8px 12px',
  color: 'var(--color-text-secondary)',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: '999px',
  fontSize: 'var(--text-sm)',
  fontWeight: 700,
  textDecoration: 'none',
}

const sectionGridStyle: React.CSSProperties = {
  width: '100%',
  maxWidth: 820,
  margin: '0 auto',
  display: 'grid',
  gap: 'var(--space-4)',
}

const sectionStyle: React.CSSProperties = {
  padding: 'var(--space-5)',
  textAlign: 'left',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-lg)',
  boxShadow: 'var(--shadow-sm)',
  scrollMarginTop: 'calc(var(--header-height) + var(--space-4))',
}

const sectionTitleStyle: React.CSSProperties = {
  margin: '0 0 var(--space-2)',
  color: 'var(--color-text-primary)',
  fontSize: 'var(--text-lg)',
  fontWeight: 800,
}

const sectionBodyStyle: React.CSSProperties = {
  margin: 0,
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-base)',
  lineHeight: 1.65,
}

const listStyle: React.CSSProperties = {
  margin: 'var(--space-3) 0 0',
  paddingLeft: '1.2rem',
  color: 'var(--color-text-secondary)',
  fontSize: 'var(--text-base)',
  lineHeight: 1.65,
}

const footerStyle: React.CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  justifyContent: 'center',
  gap: 'var(--space-4)',
  marginTop: 'var(--space-8)',
}

const linkStyle: React.CSSProperties = {
  color: 'var(--color-brand)',
  fontWeight: 800,
  textDecoration: 'none',
}
