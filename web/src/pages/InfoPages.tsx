import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PublicPageFooter } from '../components/PublicPageFooter'

type HelpTopic = {
  id: string
  title: string
  summary: string
  keywords: string[]
  marker: string
}

type HelpFaq = {
  id: string
  question: string
  answer: string
}

type PolicySection = {
  id: string
  title: string
  body?: string
  items?: string[]
  callout?: {
    title: string
    body: string
  }
}

type PolicyPageProps = {
  eyebrow: string
  title: string
  description: string
  lastUpdated: string
  sections: PolicySection[]
  closingNote: string
}

const UPDATED = 'August 22, 2026'

const helpTopics: HelpTopic[] = [
  {
    id: 'getting-started',
    title: 'Getting Started',
    summary: 'Learn the first steps from GitHub login to your first public ship.',
    keywords: ['start', 'signup', 'sign up', 'new user', 'first project'],
    marker: '01',
  },
  {
    id: 'github-login',
    title: 'GitHub Login',
    summary: 'Understand why KREVO uses GitHub and how to retry a failed login.',
    keywords: ['oauth', 'github', 'signin', 'sign in', 'login', 'account'],
    marker: 'GH',
  },
  {
    id: 'projects-ships',
    title: 'Projects & Ships',
    summary: 'Create a project home and publish progress updates tied to real work.',
    keywords: ['project', 'ship', 'updates', 'media', 'timeline'],
    marker: 'PS',
  },
  {
    id: 'discovery-feeds',
    title: 'Discovery & Feeds',
    summary: 'Browse public work, search builders, and follow interesting projects.',
    keywords: ['discover', 'feed', 'search', 'people', 'trending', 'latest'],
    marker: 'DF',
  },
  {
    id: 'collaboration',
    title: 'Collaboration',
    summary: 'Request collaboration and manage incoming or sent requests.',
    keywords: ['collaborate', 'request', 'accept', 'decline', 'project owner'],
    marker: 'CO',
  },
  {
    id: 'notifications',
    title: 'Notifications',
    summary: 'Track follow, like, comment, and collaboration activity in the app.',
    keywords: ['alerts', 'notifications', 'badge', 'read state'],
    marker: 'NT',
  },
  {
    id: 'shipscore-rank',
    title: 'ShipScore & Rank',
    summary: 'See how selected platform activity contributes to reputation signals.',
    keywords: ['score', 'rank', 'reputation', 'level', 'shipscore'],
    marker: 'SR',
  },
  {
    id: 'account-access',
    title: 'Account Access',
    summary: 'Understand session expiry, restricted accounts, and blocked write actions.',
    keywords: ['blocked', 'restricted', 'banned', 'session', 'logout'],
    marker: 'AA',
  },
]

const quickStart = [
  {
    title: 'Sign in with GitHub',
    body: 'Use your existing developer identity. KREVO never asks for your GitHub password.',
  },
  {
    title: 'Create a Project',
    body: 'Give the work a public home with context, stack details, links, and a timeline.',
  },
  {
    title: 'Publish your first Ship',
    body: 'Share meaningful progress as a project-linked update instead of a loose post.',
  },
  {
    title: 'Discover builders',
    body: 'Use feeds, search, and People to find technical builders and public work.',
  },
  {
    title: 'Follow and collaborate',
    body: 'Engage with ships, follow builders, and request collaboration when it fits.',
  },
  {
    title: 'Grow your KREVO identity',
    body: 'Your profile develops through projects, ships, collaboration, ShipScore, and rank.',
  },
]

const helpFaqs: HelpFaq[] = [
  {
    id: 'sign-in',
    question: 'How do I sign in?',
    answer: 'Use the GitHub sign-in flow from the KREVO home or auth page. If the flow fails or is cancelled, return to sign in and start the GitHub flow again.',
  },
  {
    id: 'why-github',
    question: 'Why does KREVO use GitHub?',
    answer: 'GitHub is the supported beta authentication method because KREVO is built around technical builder identity. KREVO does not receive your GitHub password.',
  },
  {
    id: 'create-project',
    question: 'How do I create a project?',
    answer: 'After signing in, open Projects and create a project with a name, context, stack details, and relevant links. Every ship should belong to a project.',
  },
  {
    id: 'what-is-ship',
    question: 'What is a Ship?',
    answer: 'A Ship is a progress update connected to a Project. Ships can include text, links, and supported media, and may appear in public feeds.',
  },
  {
    id: 'cannot-publish',
    question: 'Why cannot I publish a Ship?',
    answer: 'You may need to sign in, create a project first, refresh an expired session, or resolve an account restriction. Restricted accounts may browse and log out but cannot perform write actions.',
  },
  {
    id: 'follow-builders',
    question: 'How do I follow builders?',
    answer: 'Open People, search, public profiles, or ship pages, then follow a builder after signing in. Following helps shape your following feed.',
  },
  {
    id: 'collaboration-work',
    question: 'How does collaboration work?',
    answer: 'You can request collaboration on supported projects. Project owners can accept or decline requests. KREVO does not guarantee work, payment, or delivery outcomes.',
  },
  {
    id: 'shipscore-change',
    question: 'Why did my ShipScore change?',
    answer: 'ShipScore is a KREVO reputation signal based on selected meaningful activity in the platform. It is not a certification or objective measure of professional ability.',
  },
]

const privacySections: PolicySection[] = [
  {
    id: 'overview',
    title: 'Overview',
    body: 'KREVO uses information to provide GitHub login, builder profiles, projects, ships, discovery, collaboration, notifications, reputation signals, moderation, and basic service reliability.',
  },
  {
    id: 'github',
    title: 'Information from GitHub',
    body: 'When you sign in, KREVO requests GitHub OAuth access with read:user and user:email scopes. KREVO may receive your GitHub account identifier, username, avatar, profile fields, and email addresses made available by GitHub. KREVO does not receive your GitHub password.',
  },
  {
    id: 'provided-information',
    title: 'Information you provide',
    items: [
      'Profile details such as bio, location, and website URL.',
      'Project names, descriptions, technology tags, and external links.',
      'Ships, comments, collaboration requests, and uploaded media references.',
      'Engagement actions such as likes, follows, comment upvotes, and notification read state.',
    ],
  },
  {
    id: 'public-content',
    title: 'Public information',
    body: 'KREVO is built for public project progress. Profiles, projects, ships, comments, ShipScore, rank, follower/following counts, and search/feed appearances may be visible to other users or public visitors depending on the page. Do not publish confidential information in public areas.',
    callout: {
      title: 'Public by design',
      body: 'Projects, Ships, profiles, comments, and selected reputation information may be visible publicly as part of KREVO discovery.',
    },
  },
  {
    id: 'tokens',
    title: 'Tokens and local storage',
    body: 'The current web app stores KREVO-issued access and refresh tokens in browser localStorage. It also uses sessionStorage to remember the safe return path during GitHub login. Logout clears local tokens and asks the API to revoke current tokens.',
  },
  {
    id: 'security-data',
    title: 'Operational and security data',
    body: 'KREVO may process request metadata, rate-limit records, token blacklist entries, OAuth state records, audit logs, and abuse-prevention signals needed to operate and protect the beta service.',
  },
  {
    id: 'reputation',
    title: 'Personalization and reputation data',
    body: 'KREVO stores selected behavioral and scoring records to power discovery, personalization, ShipScore, and rank features. These records are product signals, not third-party advertising profiles.',
  },
  {
    id: 'retention',
    title: 'Retention',
    items: [
      'OAuth state records are short-lived and currently expire after about 10 minutes.',
      'Notifications, behavioral signals, and ShipScore event logs currently expire after about 90 days.',
      'Admin audit logs currently expire after about 365 days.',
      'Account, profile, project, ship, comment, follow, collaboration, and media-reference retention is not yet governed by a finalized public deletion policy.',
    ],
  },
  {
    id: 'providers',
    title: 'Service providers',
    body: 'KREVO uses GitHub for authentication and may use hosting, database, cache, and media-storage providers to run the service. Provider details may change as the beta infrastructure evolves.',
  },
  {
    id: 'analytics',
    title: 'Analytics and advertising',
    body: 'The current codebase does not include Google Analytics, Mixpanel, Segment, PostHog, Meta Pixel, Hotjar, or an advertising system. KREVO may still use internal logs and product records to operate the service.',
  },
  {
    id: 'choices',
    title: 'Your choices',
    body: 'You can choose what you publish publicly and can log out from your session. A self-service account deletion workflow is not currently available in the beta; deletion and privacy-request handling require a defined operating process before broad launch.',
  },
  {
    id: 'security',
    title: 'Security',
    body: 'KREVO uses technical measures intended to protect the service, but no system can guarantee absolute security. Users should avoid posting secrets, credentials, private keys, or confidential project material.',
  },
  {
    id: 'legal-review',
    title: 'Children and legal review',
    body: 'KREVO is not intentionally designed for young children. Age requirements, privacy rights by jurisdiction, international data handling, legal entity details, and contact channels require qualified legal review before broad public launch.',
  },
]

const termsSections: PolicySection[] = [
  {
    id: 'acceptance',
    title: 'Acceptance',
    body: 'By using KREVO, you agree to use the service responsibly and follow these beta terms. If you do not agree, do not use the service.',
  },
  {
    id: 'using-krevo',
    title: 'Using KREVO',
    body: 'KREVO is intended for sharing legitimate technical project progress, discovering builders, engaging with public ships, and requesting collaboration respectfully.',
  },
  {
    id: 'accounts',
    title: 'Accounts and GitHub',
    body: 'GitHub OAuth is the supported account method for this beta. You are responsible for your GitHub account and KREVO session. GitHub is a third-party authentication provider and does not endorse KREVO.',
  },
  {
    id: 'content',
    title: 'User content',
    body: 'You are responsible for projects, ships, comments, links, screenshots, uploaded media, and other content you publish. Users retain ownership of content they create, subject to the permissions KREVO needs to host, display, process, and operate that content through the service. Exact commercial content-license language requires legal review.',
  },
  {
    id: 'public-progress',
    title: 'Public project progress',
    body: 'KREVO is designed around public building. Content you publish may be visible in profiles, feeds, search, project pages, and ship pages. Do not post confidential information, private credentials, or content you do not have rights to share.',
  },
  {
    id: 'acceptable-use',
    title: 'Acceptable use',
    items: [
      'Do not harass, threaten, impersonate, spam, or abuse other builders.',
      'Do not upload malicious files or attempt unauthorized access.',
      'Do not manipulate ShipScore, rankings, feeds, follows, likes, comments, or collaboration flows.',
      'Do not use KREVO to publish unlawful, deceptive, or rights-infringing content.',
    ],
  },
  {
    id: 'collaboration',
    title: 'Collaboration',
    body: 'Collaboration requests are user-to-user interactions. KREVO is not an employer, recruiter, contractor, payment provider, or guarantor of collaboration outcomes.',
  },
  {
    id: 'shipscore',
    title: 'ShipScore and rank',
    body: 'ShipScore and rank are KREVO platform features designed to reflect selected activity and contribution signals within KREVO. Their rules may evolve as the product develops. They do not verify professional skill, guarantee employment eligibility, or certify competence.',
    callout: {
      title: 'Product signal, not a certification',
      body: 'ShipScore helps show selected activity inside KREVO. It is not a guarantee of professional ability, employment eligibility, or future performance.',
    },
  },
  {
    id: 'moderation',
    title: 'Moderation and restrictions',
    body: 'KREVO may restrict accounts or write actions when needed to protect the service and community. Restricted users may still be able to browse public content and log out. A formal public appeal process is not yet defined for beta.',
  },
  {
    id: 'availability',
    title: 'Service availability',
    body: 'KREVO is a beta product. Features may change, break, be interrupted, or be removed as the service evolves. KREVO does not promise continuous availability, guaranteed uptime, or zero data loss.',
  },
  {
    id: 'third-party-services',
    title: 'Third-party services',
    body: 'KREVO depends on third-party services such as GitHub authentication and infrastructure providers. Their own terms and policies may apply when you interact with them.',
  },
  {
    id: 'paid-features',
    title: 'No paid features in beta',
    body: 'The current MVP does not include paid subscriptions, billing, renewals, or refunds. Payment terms should be added only if monetization is introduced.',
  },
  {
    id: 'legal-review',
    title: 'Legal review needed',
    body: 'Governing law, dispute resolution, liability limitations, content licensing details, privacy rights, age requirements, legal entity name, and official contact channels require qualified legal review before broad commercial or public launch.',
  },
]

export function HelpPage() {
  const { user } = useAuth()
  const [query, setQuery] = useState('')
  const [openFaqId, setOpenFaqId] = useState(helpFaqs[0]?.id ?? '')

  useEffect(() => {
    document.title = 'KREVO Help Center'
  }, [])

  const normalizedQuery = query.trim().toLowerCase()
  const filteredTopics = useMemo(() => {
    if (!normalizedQuery) return helpTopics
    return helpTopics.filter(topic => {
      const haystack = [topic.title, topic.summary, ...topic.keywords].join(' ').toLowerCase()
      return haystack.includes(normalizedQuery)
    })
  }, [normalizedQuery])

  const filteredFaqs = useMemo(() => {
    if (!normalizedQuery) return helpFaqs
    return helpFaqs.filter(faq => `${faq.question} ${faq.answer}`.toLowerCase().includes(normalizedQuery))
  }, [normalizedQuery])

  return (
    <article className="public-page public-help-page" aria-labelledby="help-title">
      <section className="public-hero public-hero-centered">
        <p className="public-eyebrow">Help Center</p>
        <h1 id="help-title">How can we help?</h1>
        <p>
          Find answers about your account, projects, Ships, collaboration, discovery, and ShipScore.
        </p>
        <form className="help-search-form" role="search" onSubmit={event => event.preventDefault()}>
          <label htmlFor="help-search">Search KREVO help</label>
          <div className="help-search-box">
            <input
              id="help-search"
              value={query}
              onChange={event => setQuery(event.target.value)}
              type="search"
              placeholder="Search KREVO help..."
            />
            {query && (
              <button type="button" onClick={() => setQuery('')}>
                Clear
              </button>
            )}
          </div>
          {query && (
            <p className="help-search-status" role="status">
              Showing {filteredTopics.length + filteredFaqs.length} result{filteredTopics.length + filteredFaqs.length === 1 ? '' : 's'} for "{query}".
            </p>
          )}
        </form>
      </section>

      <section className="public-section" aria-labelledby="popular-topics">
        <div className="public-section-heading">
          <p className="public-eyebrow">Popular topics</p>
          <h2 id="popular-topics">Choose where you need help.</h2>
        </div>
        {filteredTopics.length > 0 ? (
          <div className="help-topic-grid">
            {filteredTopics.map(topic => (
              <a key={topic.id} className="help-topic-card" href={`#${topic.id}`}>
                <span className="help-topic-marker" aria-hidden="true">{topic.marker}</span>
                <span>
                  <strong>{topic.title}</strong>
                  <small>{topic.summary}</small>
                  <span className="help-topic-action">View guide</span>
                </span>
              </a>
            ))}
          </div>
        ) : (
          <div className="public-empty-state" role="status">
            <h3>No matching help topics</h3>
            <p>Try searching for projects, GitHub login, collaboration, notifications, or ShipScore.</p>
            <button type="button" onClick={() => setQuery('')}>Clear search</button>
          </div>
        )}
      </section>

      <section className="public-section" aria-labelledby="quick-start">
        <div className="public-section-heading">
          <p className="public-eyebrow">Quick start</p>
          <h2 id="quick-start">From GitHub identity to visible progress.</h2>
        </div>
        <div className="help-step-grid">
          {quickStart.map((step, index) => (
            <article key={step.title} className="help-step-card">
              <span aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <h3>{step.title}</h3>
              <p>{step.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="public-section help-guide-section" aria-labelledby="guides">
        <div className="public-section-heading">
          <p className="public-eyebrow">Guides</p>
          <h2 id="guides">Supported product areas.</h2>
        </div>
        <div className="help-guide-list">
          <HelpGuide id="getting-started" title="Getting Started">
            <ul>
              <li>Sign in with GitHub, then KREVO creates or resolves your builder account.</li>
              <li>Review your profile details, then create your first project from Projects.</li>
              <li>Publish Ships as progress updates connected to a project.</li>
              <li>Use feeds, search, and People to discover builders and public work.</li>
            </ul>
          </HelpGuide>
          <HelpGuide id="github-login" title="GitHub Login">
            <p>GitHub is the supported authentication method for the current beta. If login fails or is cancelled, return to the sign-in page and start the GitHub flow again. KREVO does not ask for or store a GitHub password.</p>
          </HelpGuide>
          <HelpGuide id="projects-ships" title="Projects & Ships">
            <p>A Project represents what you are building. A Ship is a progress update linked to a Project. Ships can include text, links, and supported media, and may be discoverable in public feeds.</p>
          </HelpGuide>
          <HelpGuide id="discovery-feeds" title="Discovery & Feeds">
            <p>You can browse public ships, search builders and projects, follow builders, like ships, comment, and use the following feed after signing in.</p>
          </HelpGuide>
          <HelpGuide id="collaboration" title="Collaboration">
            <p>KREVO supports collaboration requests with incoming and sent views. A request can be accepted or declined by the project owner; KREVO does not guarantee that a collaboration will lead to work, payment, or delivery.</p>
          </HelpGuide>
          <HelpGuide id="notifications" title="Notifications">
            <p>In-app notifications help you find follow, like, comment, and collaboration activity. Email and real-time push notifications are not part of the current beta scope.</p>
          </HelpGuide>
          <HelpGuide id="shipscore-rank" title="ShipScore & Rank">
            <p>ShipScore is KREVO's platform reputation signal based on selected meaningful activity inside KREVO. It is not a certification, employment credential, or objective measure of professional ability.</p>
          </HelpGuide>
          <HelpGuide id="account-access" title="Account Access">
            <p>Some accounts may be restricted from write actions such as publishing, liking, commenting, following, or uploading. Restricted users may still browse public content and log out.</p>
          </HelpGuide>
        </div>
      </section>

      <section className="public-section" aria-labelledby="common-questions">
        <div className="public-section-heading">
          <p className="public-eyebrow">Common questions</p>
          <h2 id="common-questions">Fast answers for beta users.</h2>
        </div>
        {filteredFaqs.length > 0 ? (
          <div className="help-faq-list">
            {filteredFaqs.map(faq => {
              const isOpen = openFaqId === faq.id
              return (
                <div key={faq.id} className="help-faq-item">
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    aria-controls={`${faq.id}-answer`}
                    onClick={() => setOpenFaqId(isOpen ? '' : faq.id)}
                  >
                    <span>{faq.question}</span>
                    <span aria-hidden="true">{isOpen ? '-' : '+'}</span>
                  </button>
                  <div id={`${faq.id}-answer`} hidden={!isOpen} className="help-faq-panel">
                    <p>{faq.answer}</p>
                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          <div className="public-empty-state" role="status">
            <h3>No matching questions</h3>
            <p>Clear the search to browse all common questions.</p>
            <button type="button" onClick={() => setQuery('')}>Clear search</button>
          </div>
        )}
      </section>

      <section className="public-support-block" aria-labelledby="still-need-help">
        <div>
          <p className="public-eyebrow">Still need help?</p>
          <h2 id="still-need-help">Use official KREVO channels for beta support.</h2>
          <p>
            Official support contact information will be published through KREVO channels before broader launch. For now, keep sensitive credentials, private keys, and confidential project data out of public content.
          </p>
        </div>
        <Link className="public-button public-button-primary" to={user ? '/feed' : '/auth'}>
          {user ? 'Go to KREVO' : 'Continue with GitHub'}
        </Link>
      </section>

      <PublicPageFooter />
    </article>
  )
}

function HelpGuide({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="help-guide-card" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`}>{title}</h3>
      {children}
    </section>
  )
}

export function PrivacyPage() {
  return (
    <PolicyPage
      eyebrow="Privacy"
      title="KREVO Privacy Policy"
      description="This beta privacy summary explains how KREVO currently uses information to operate the builder network. It is written for transparency during beta and should receive professional legal review before broad commercial or public launch."
      lastUpdated={UPDATED}
      sections={privacySections}
      closingNote="This summary is intentionally factual for beta. Privacy rights by jurisdiction, official contact channels, legal entity details, and deletion workflows require qualified legal review before broad public launch."
    />
  )
}

export function TermsPage() {
  return (
    <PolicyPage
      eyebrow="Terms"
      title="KREVO Terms of Use"
      description="These beta terms explain the basic expectations for using KREVO while the product is being validated. They are practical product terms, not a substitute for professional legal review before commercial launch."
      lastUpdated={UPDATED}
      sections={termsSections}
      closingNote="These beta terms preserve the current product scope. Final governing law, liability, content-license, and contact language should be written with qualified legal counsel."
    />
  )
}

function PolicyPage({ eyebrow, title, description, lastUpdated, sections, closingNote }: PolicyPageProps) {
  useEffect(() => {
    document.title = title
  }, [title])

  return (
    <article className="public-page policy-page" aria-labelledby="policy-title">
      <header className="public-hero policy-hero">
        <p className="public-eyebrow">{eyebrow}</p>
        <h1 id="policy-title">{title}</h1>
        <p>{description}</p>
        <span className="policy-date">Last updated: {lastUpdated}</span>
      </header>

      <div className="policy-layout">
        <aside className="policy-toc" aria-label={`${title} sections`}>
          <p>On this page</p>
          <nav>
            {sections.map(section => (
              <a key={section.id} href={`#${section.id}`}>
                {section.title}
              </a>
            ))}
          </nav>
        </aside>

        <div className="policy-content">
          {sections.map(section => (
            <section key={section.id} id={section.id} className="policy-section" aria-labelledby={`${section.id}-title`}>
              <h2 id={`${section.id}-title`}>{section.title}</h2>
              {section.body && <p>{section.body}</p>}
              {section.items && (
                <ul>
                  {section.items.map(item => <li key={item}>{item}</li>)}
                </ul>
              )}
              {section.callout && (
                <aside className="policy-callout">
                  <strong>{section.callout.title}</strong>
                  <p>{section.callout.body}</p>
                </aside>
              )}
            </section>
          ))}

          <section className="policy-closing" aria-label="Important note">
            <h2>Important beta note</h2>
            <p>{closingNote}</p>
          </section>
        </div>
      </div>

      <PublicPageFooter />
    </article>
  )
}
