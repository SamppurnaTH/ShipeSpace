import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getTrendingFeed } from '../../api/endpoints'
import { getGitHubLoginErrorMessage, startGitHubLogin } from '../../auth/githubLogin'
import builderCommunityImage from '../../assets/home/shipspace-builder-community.png'
import builderProfileTimelineImage from '../../assets/home/shipspace-builder-profile-timeline.png'
import audienceVisualImage from '../../assets/home/shipspace-audience-visual.png'
import launchPadVisualImage from '../../assets/home/shipspace-launch-pad-visual.png'
import reputationVisualImage from '../../assets/home/shipspace-reputation-visual.png'
import workflowVisualImage from '../../assets/home/shipspace-workflow-visual.png'
import type { FeedItem } from '../../hooks/useFeed'
import { ButtonLoader, ShipCardSkeleton } from '../common/Loading'
import { HomeEmptyState } from './HomeEmptyState'
import { HomeShipPreview } from './HomeShipPreview'

type PreviewState = 'loading' | 'done' | 'empty' | 'error'
type PrimaryNavItem =
  | { label: string; kind: 'section'; href: string }
  | { label: string; kind: 'route'; to: string }

const RANKS = [
  { name: 'Docked',    color: '#6b7280', desc: 'Getting started'    },
  { name: 'Launcher',  color: '#4caf7d', desc: 'Active builder'     },
  { name: 'Orbiter',   color: '#0a66c2', desc: 'Consistent shipper' },
  { name: 'Navigator', color: '#a78bfa', desc: 'Community builder'  },
  { name: 'Commander', color: '#f5a623', desc: 'Top tier'           },
]

const CONTENT_TOPICS = [
  'Web apps', 'AI projects', 'Open source', 'Student builds', 'Indie products',
  'DevTools', 'Mobile apps', 'APIs', 'Design engineering', 'Hardware',
]

const PROJECT_AREAS = [
  'Frontend', 'Backend', 'Full stack', 'Machine learning', 'SaaS',
  'Portfolio projects', 'Hackathon builds', 'Developer tools', 'Mobile', 'Data',
  'Open source', 'Campus projects', 'APIs', 'Design systems', 'Infrastructure',
]

const TOOL_TOPICS = [
  'Project management', 'Code review', 'Issue tracking', 'Design tools',
  'Deployment', 'Testing', 'Analytics', 'Documentation', 'Community',
]

const LEARNING_TOPICS = [
  'React', 'TypeScript', 'Spring Boot', 'MongoDB', 'PostgreSQL', 'Docker',
  'System design', 'Product design', 'Public APIs', 'Testing', 'DevOps', 'Security',
]

const PRIMARY_NAV_ITEMS: PrimaryNavItem[] = [
  { label: 'Top Content', kind: 'section', href: '#top-content' },
  { label: 'People', kind: 'route', to: '/people' },
  { label: 'Learning', kind: 'section', href: '#learning' },
  { label: 'Projects', kind: 'section', href: '#projects' },
  { label: 'ShipScore', kind: 'section', href: '#shipscore' },
]

const AUDIENCES = [
  {
    title: 'Find technical collaborators',
    body: 'Meet builders shipping with the same stacks, goals, and project energy.',
  },
  {
    title: 'Follow student builders',
    body: 'Track campus projects, hackathon work, and early proof-of-work timelines.',
  },
  {
    title: 'Discover indie hackers',
    body: 'See what makers are launching before it becomes a polished case study.',
  },
]

const FOOTER_GROUPS = [
  {
    title: 'Start shipping',
    links: [
      { label: 'Create project', to: '/auth' },
      { label: 'Publish ship', to: '/auth' },
      { label: 'Explore ships', to: '/trending' },
      { label: 'Find builders', to: '/people' },
    ],
  },
  {
    title: 'Build in public',
    links: [
      { label: 'Project timelines', to: '/trending' },
      { label: 'ShipScore', to: '#shipscore' },
      { label: 'Builder profiles', to: '/people' },
      { label: 'Stack discovery', to: '/trending' },
    ],
  },
  {
    title: 'Community',
    links: [
      { label: 'Collaborators', to: '/people' },
      { label: 'Student builders', to: '/people' },
      { label: 'Indie hackers', to: '/people' },
      { label: 'Open source', to: '/trending' },
    ],
  },
  {
    title: 'KREVO',
    links: [
      { label: 'About KREVO', to: '/about' },
      { label: 'Public discovery', to: '/trending' },
      { label: 'GitHub login', to: '/auth' },
      { label: 'Builder search', to: '/people' },
    ],
  },
  {
    title: 'Legal & support',
    links: [
      { label: 'Help Center', to: '/help' },
      { label: 'Privacy Policy', to: '/privacy' },
      { label: 'Terms of Use', to: '/terms' },
      { label: 'Beta status', to: '/about' },
    ],
  },
]

export function PublicHomePage() {
  const [items, setItems]         = useState<FeedItem[]>([])
  const [state, setState]         = useState<PreviewState>('loading')
  const [loginError, setLoginError] = useState('')
  const [loginLoading, setLoginLoading] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  useEffect(() => {
    let alive = true
    setState('loading')
    getTrendingFeed(0, 3)
      .then(res => {
        if (!alive) return
        const content = (res.data.content ?? []) as FeedItem[]
        setItems(content)
        setState(content.length > 0 ? 'done' : 'empty')
      })
      .catch(() => { if (alive) setState('error') })
    return () => { alive = false }
  }, [])

  const handleLogin = async () => {
    setLoginError('')
    setLoginLoading(true)
    try {
      await startGitHubLogin()
    } catch (err) {
      setLoginError(getGitHubLoginErrorMessage(err))
      setLoginLoading(false)
    }
  }

  const closeMobileMenu = () => setMobileMenuOpen(false)

  return (
    <div className="home-public">

      <header className="home-public-nav">
        <div className="home-public-nav-inner">
          <Link to="/" className="home-brand" aria-label="KREVO home">
            <span className="home-brand-mark"><ShipSpaceMark /></span>
            </Link>

          <nav className="home-marketing-links" aria-label="KREVO navigation">
            {PRIMARY_NAV_ITEMS.map(item => (
              item.kind === 'route'
                ? <Link key={item.label} to={item.to}>{item.label}</Link>
                : <a key={item.label} href={item.href}>{item.label}</a>
            ))}
          </nav>

          <div className="home-header-actions">
            <Link className="home-header-signin" to="/auth?mode=signin">Sign in</Link>
            <button className="home-btn home-btn-primary" onClick={handleLogin} disabled={loginLoading}>
              {loginLoading ? (
                <ButtonLoader label="Connecting..." />
              ) : (
                <>
                  <GitHubIcon />
                  Continue with GitHub
                </>
              )}
            </button>
          </div>

          <button
            className="home-mobile-menu-button"
            type="button"
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileMenuOpen}
            aria-controls="home-mobile-menu"
            onClick={() => setMobileMenuOpen(open => !open)}
          >
            <span /><span /><span />
          </button>
        </div>

        {mobileMenuOpen && (
          <div id="home-mobile-menu" className="home-mobile-menu">
            {PRIMARY_NAV_ITEMS.map(item => (
              item.kind === 'route'
                ? <Link key={item.label} to={item.to} onClick={closeMobileMenu}>{item.label}</Link>
                : <a key={item.label} href={item.href} onClick={closeMobileMenu}>{item.label}</a>
            ))}
            <Link to="/auth?mode=signin" onClick={closeMobileMenu}>Sign in</Link>
            <Link to="/signup?mode=signup" onClick={closeMobileMenu}>Sign up</Link>
            <button className="home-btn home-btn-primary" type="button" onClick={() => { closeMobileMenu(); void handleLogin() }} disabled={loginLoading}>
              {loginLoading ? (
                <ButtonLoader label="Connecting..." />
              ) : (
                <>
                  <GitHubIcon />
                  Continue with GitHub
                </>
              )}
            </button>
          </div>
        )}
      </header>

      <main>

        <section className="home-hero" aria-labelledby="home-hero-title">
          <div className="home-hero-copy">
            <p className="home-eyebrow">KREVO for builders</p>
            <h1 id="home-hero-title">Welcome to your builder community.</h1>
            <p>
              Create projects, publish ships, and discover technical builders
              through real progress instead of polished resumes.
            </p>
            <div className="home-cta-row">
              <button className="home-btn home-btn-primary home-btn-large" onClick={handleLogin} disabled={loginLoading}>
                {loginLoading ? (
                  <ButtonLoader label="Connecting..." />
                ) : (
                  <>
                    <GitHubIcon />
                    Continue with GitHub
                  </>
                )}
              </button>
              <Link className="home-btn home-btn-secondary home-btn-large" to="/trending">
                Explore ships
              </Link>
            </div>
            <p className="home-hero-support">
              New to KREVO? Join with GitHub and start from your developer identity.
            </p>
            {loginError && <p className="home-form-error" role="alert">{loginError}</p>}
          </div>

          <div className="home-hero-preview" aria-label="KREVO product preview">
            <div className="home-hero-image-frame">
              <img
                src={builderCommunityImage}
                alt="Illustration of builders collaborating around KREVO profile, project, and feed interfaces"
              />
            </div>
          </div>
        </section>

        <section id="top-content" className="home-section home-topic-section" aria-labelledby="home-value-title">
          <div className="home-split-heading">
            <h2 id="home-value-title">Explore top KREVO content</h2>
            <p>Discover relevant ships, project timelines, and builder updates by topic.</p>
          </div>
          <div className="home-chip-cloud" aria-label="KREVO content topics">
            {CONTENT_TOPICS.map(topic => <Link key={topic} to="/trending">{topic}</Link>)}
          </div>
        </section>

        <section className="home-section home-trending-section" aria-labelledby="home-trending-title">
          <div className="home-trending-inner">
            <div className="home-section-header">
              <p className="home-eyebrow">Public discovery</p>
              <h2 id="home-trending-title">See what builders are shipping.</h2>
              <p>
                Anonymous visitors can browse public ships and discover builders
                before joining. No account required.
              </p>
              <div className="home-trending-actions">
                <Link to="/people" className="home-btn home-btn-secondary">
                  Browse all builders
                </Link>
              </div>
            </div>

            <div className="home-trending-panel">
              <div className="home-trending-topbar">
                <span>Trending ships</span>
                <Link to="/trending" className="home-trending-view-all">View all</Link>
              </div>
              <div className="home-preview-list">
                {state === 'loading' && (
                  <div aria-busy="true" aria-label="Loading public ships">
                    <ShipCardSkeleton />
                    <ShipCardSkeleton />
                    <ShipCardSkeleton />
                  </div>
                )}
                {state === 'done' && items.map(item => (
                  <HomeShipPreview key={item.ship.id} item={item} />
                ))}
                {state === 'empty' && (
                  <HomeEmptyState
                    title="No public ships yet"
                    body="Be the first builder to publish progress and show up here."
                  />
                )}
                {state === 'error' && (
                  <HomeEmptyState
                    title="Public feed unavailable"
                    body="KREVO is reachable, but the trending preview could not load right now."
                    tone="error"
                    action={<Link className="home-link" to="/trending">Open trending page</Link>}
                  />
                )}
              </div>
            </div>
          </div>
        </section>

        <section id="projects" className="home-section home-topic-section home-topic-section--soft" aria-labelledby="home-projects-title">
          <div className="home-split-heading">
            <h2 id="home-projects-title">Find the right project or collaborator for you</h2>
            <p>Explore builders by stack, project type, and the kind of work they are shipping.</p>
          </div>
          <div className="home-chip-cloud" aria-label="Project and collaborator categories">
            {PROJECT_AREAS.map(area => <Link key={area} to="/people">{area}</Link>)}
          </div>
        </section>

        <section className="home-wide-cta" aria-labelledby="home-post-title">
          <div>
            <h2 id="home-post-title">Post your project for builders to see</h2>
            <p>Every project gets a public timeline, a stack, ships, and reputation signals.</p>
          </div>
          <Link className="home-btn home-btn-secondary home-btn-large" to="/signup?mode=signup">
            Create a project
          </Link>
        </section>

        <section className="home-section home-topic-section" aria-labelledby="home-tools-title">
          <div className="home-split-heading">
            <h2 id="home-tools-title">Discover builder tools and workflows</h2>
            <p>Find the practices and tooling other builders use to move projects forward.</p>
          </div>
          <div className="home-chip-cloud" aria-label="Builder tool topics">
            {TOOL_TOPICS.map(topic => <Link key={topic} to="/trending">{topic}</Link>)}
          </div>
        </section>

        <section className="home-section home-editorial-grid" aria-label="KREVO value highlights">
          <article>
            <h2>Let the right builders know what you are shipping</h2>
            <p>Turn project work into visible progress other builders can follow, discuss, and remember.</p>
          </article>
          <article>
            <h2>Conversations today can become collaborations tomorrow</h2>
            <p>Ships give collaborators context before they reach out, so conversations start closer to the work.</p>
          </article>
          <article>
            <h2>Stay up to date on what builders are launching</h2>
            <p>Browse public ships, project timelines, and builder profiles without creating an account.</p>
          </article>
        </section>

        <section className="home-image-story" aria-labelledby="home-image-story-title">
          <div className="home-image-story-media">
            <img
              src={builderProfileTimelineImage}
              alt="Illustration of a KREVO builder profile connected to project timeline updates and reputation"
            />
          </div>
          <div className="home-image-story-copy">
            <p className="home-eyebrow">Builder profiles</p>
            <h2 id="home-image-story-title">Your profile grows from what you ship.</h2>
            <p>
              KREVO turns project updates into a living builder profile:
              ships, milestones, stack context, collaborators, and reputation in one place.
            </p>
            <Link className="home-btn home-btn-secondary" to="/trending">See public ships</Link>
          </div>
        </section>

        <section id="learning" className="home-section home-topic-section home-topic-section--soft" aria-labelledby="home-learning-title">
          <div className="home-split-heading">
            <h2 id="home-learning-title">Learn the stacks builders use</h2>
            <p>Choose a topic and see real updates from people building with it.</p>
          </div>
          <div className="home-chip-cloud" aria-label="Builder learning topics">
            {LEARNING_TOPICS.map(topic => <Link key={topic} to="/trending">{topic}</Link>)}
          </div>
        </section>

        <section id="how-it-works" className="home-section home-how-section" aria-labelledby="home-how-title">
          <div className="home-section-header home-section-header--centered">
            <p className="home-eyebrow">How it works</p>
            <h2 id="home-how-title">From GitHub identity to visible momentum.</h2>
          </div>
          <div className="home-visual-story home-visual-story--workflow">
            <img
              src={workflowVisualImage}
              alt="Illustration showing a developer identity connecting to a project card and published ship timeline"
            />
            <div className="home-visual-legend" aria-label="KREVO workflow">
              <span>Connect GitHub</span>
              <span>Create a project</span>
              <span>Publish ships</span>
            </div>
          </div>
        </section>

        <section id="shipscore" className="home-reputation-section" aria-labelledby="home-reputation-title">
          <div className="home-reputation-inner">
            <div className="home-reputation-copy">
              <p className="home-eyebrow home-eyebrow--accent">ShipScore</p>
              <h2 id="home-reputation-title">Reputation built from real progress.</h2>
              <p>Every meaningful project update contributes to your public builder signal.</p>
              <div className="home-rank-strip" aria-label="Builder rank tiers">
                {RANKS.map(r => <span key={r.name} style={{ borderColor: r.color }}>{r.name}</span>)}
              </div>
            </div>
            <div className="home-visual-story home-visual-story--reputation">
              <img
                src={reputationVisualImage}
                alt="Illustration showing project updates becoming builder reputation, rank progress, and community signal"
              />
            </div>
          </div>
        </section>

        <section className="home-section home-audience-section" aria-labelledby="home-audience-title">
          <div className="home-split-heading">
            <h2 id="home-audience-title">Who is KREVO for?</h2>
            <p>Developers, students, indie hackers, and makers building in public.</p>
          </div>
          <div className="home-audience-visual-wrap">
            <img
              src={audienceVisualImage}
              alt="Illustration of developers, students, indie hackers, and collaborators connected through project cards"
            />
            <div className="home-audience-list" aria-label="KREVO audiences">
              {AUDIENCES.map(item => (
                <Link key={item.title} to="/people">
                  <span>
                    <strong>{item.title}</strong>
                    <small>{item.body}</small>
                  </span>
                </Link>
              ))}
            </div>
          </div>
        </section>

        <section className="home-final-cta" aria-labelledby="home-final-title">
          <div className="home-final-inner">
            <div className="home-final-copy">
              <p className="home-eyebrow home-eyebrow--light">Launch pad</p>
              <h2 id="home-final-title">Turn your next commit into visible momentum.</h2>
              <p>Start with GitHub, create a project log, and let each ship become part of your builder story.</p>
              <div className="home-cta-row">
                <button className="home-btn home-btn-cta-primary home-btn-large" onClick={handleLogin} disabled={loginLoading}>
                  {loginLoading ? (
                    <ButtonLoader label="Connecting..." />
                  ) : (
                    <>
                      <GitHubIcon />
                      Continue with GitHub
                    </>
                  )}
                </button>
                <Link className="home-btn home-btn-cta-secondary home-btn-large" to="/trending">
                  Explore ships
                </Link>
              </div>
            </div>
            <div className="home-launch-card" aria-label="KREVO launch preview">
              <div className="home-launch-image">
                <img
                  src={launchPadVisualImage}
                  alt="Illustration of a builder turning project work into launch momentum on KREVO"
                />
              </div>
              <div className="home-launch-summary">
                <div className="home-launch-track" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </div>
                <div className="home-launch-project">
                  <span>Project log</span>
                  <strong>Portfolio OS</strong>
                </div>
                <div className="home-launch-ship">
                  <span>Latest ship</span>
                  <strong>Auth flow polished</strong>
                </div>
                <div className="home-launch-score">
                  <span>ShipScore</span>
                  <strong>+42</strong>
                </div>
              </div>
            </div>
          </div>
        </section>

        <nav aria-label="Footer Navigation" className="home-footer-nav-wrapper">
        <footer className="home-footer" aria-label="KREVO footer">
          <div className="home-footer-inner">
            <Link to="/" className="home-footer-brand" aria-label="KREVO home">
              <span className="home-brand-mark"><img src="/footer.png" alt="KREVO Footer Logo" className="krevo-logo-img" /></span>
            </Link>
            {FOOTER_GROUPS.map(group => (
              <div key={group.title} className="home-footer-group">
                <h2>{group.title}</h2>
                {group.links.map(link => <Link key={link.label} to={link.to}>{link.label}</Link>)}
              </div>
            ))}
          </div>
          <div className="home-footer-bottom">
            <span>&copy; 2026 KREVO</span>
            <span>Built for people who ship real projects.</span>
          </div>
        </footer>
        </nav>

      </main>
    </div>
  )
}


function GitHubIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.44 9.8 8.21 11.39.6.11.82-.26.82-.58 0-.28-.01-1.03-.02-2.02-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.75.08-.73.08-.73 1.2.08 1.84 1.24 1.84 1.24 1.07 1.83 2.81 1.3 3.49.99.11-.78.42-1.3.76-1.6-2.66-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.12-.3-.54-1.52.12-3.18 0 0 1.01-.32 3.3 1.23A11.5 11.5 0 0 1 12 5.31c1.02.01 2.05.14 3.01.41 2.29-1.55 3.3-1.23 3.3-1.23.66 1.66.24 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.62-5.48 5.92.43.37.81 1.1.81 2.22 0 1.6-.01 2.89-.01 3.28 0 .32.22.69.83.57A12.01 12.01 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
    </svg>
  )
}

function ShipSpaceMark() {
  return <img src="/logo.png" alt="KREVO Logo" className="krevo-logo-img" />
}








