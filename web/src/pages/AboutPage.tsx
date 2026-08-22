import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PublicPageFooter } from '../components/PublicPageFooter'

const steps = [
  {
    title: 'Create',
    body: 'Start a project and give it a clear public home.',
  },
  {
    title: 'Ship',
    body: 'Share meaningful progress tied to real work.',
  },
  {
    title: 'Discover',
    body: 'Find builders, projects, and technical ideas.',
  },
  {
    title: 'Collaborate',
    body: 'Work with people building interesting things.',
  },
  {
    title: 'Evolve',
    body: 'Build reputation through visible project momentum.',
  },
]

const audiences = ['Developers', 'Students', 'Indie builders', 'Makers', 'Open-source contributors']

export function AboutPage() {
  const { user } = useAuth()

  useEffect(() => {
    document.title = 'About KREVO'
  }, [])

  return (
    <article className="public-page about-page" aria-labelledby="about-title">
      <section className="public-hero about-hero">
        <div>
          <p className="public-eyebrow">About KREVO</p>
          <h1 id="about-title">Create. Ship. Evolve.</h1>
          <p>
            KREVO is a builder network for people who turn ideas into real projects. Your work should speak louder than your title.
          </p>
          <div className="public-actions">
            <Link className="public-button public-button-primary" to={user ? '/projects' : '/auth'}>
              {user ? 'Create a project' : 'Continue with GitHub'}
            </Link>
            <Link className="public-button public-button-secondary" to="/trending">
              Explore KREVO
            </Link>
          </div>
        </div>
        <aside className="about-identity-card" aria-label="KREVO identity model">
          <span>Builder identity</span>
          <h2>Projects + Ships + Collaboration + ShipScore</h2>
          <p>Build a public profile from the work you actually move forward.</p>
          <div>
            <strong>Project timeline</strong>
            <strong>Visible progress</strong>
            <strong>Reputation signals</strong>
          </div>
        </aside>
      </section>

      <section className="public-section about-story-section" aria-labelledby="why-krevo">
        <p className="public-eyebrow">Why KREVO</p>
        <h2 id="why-krevo">Ideas are easy to describe. Progress is harder to prove.</h2>
        <p>
          Most builder profiles focus on polished outcomes after the work is finished. KREVO is designed for the space in between: the releases, fixes, decisions, experiments, and collaborations that show how a builder actually moves.
        </p>
      </section>

      <section className="public-section" aria-labelledby="how-krevo-works">
        <div className="public-section-heading">
          <p className="public-eyebrow">How it works</p>
          <h2 id="how-krevo-works">A simple loop for building in public.</h2>
        </div>
        <div className="about-step-grid">
          {steps.map((step, index) => (
            <article key={step.title} className="about-step-card">
              <span aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <h3>{step.title}</h3>
              <p>{step.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="public-section about-audience-section" aria-labelledby="who-krevo-is-for">
        <div>
          <p className="public-eyebrow">Who it is for</p>
          <h2 id="who-krevo-is-for">Builders who want progress to be visible.</h2>
          <p>
            KREVO is for technical people creating real projects and looking for a clearer way to share work, learn from others, and find collaborators without turning progress into a resume.
          </p>
        </div>
        <div className="about-audience-list" aria-label="KREVO audiences">
          {audiences.map(audience => <span key={audience}>{audience}</span>)}
        </div>
      </section>

      <section className="public-support-block about-cta-block" aria-labelledby="build-your-identity">
        <div>
          <p className="public-eyebrow">Build your identity</p>
          <h2 id="build-your-identity">Ship your progress, not just your final product.</h2>
          <p>
            Start with GitHub, create a project, publish Ships, and let your profile grow through visible work.
          </p>
        </div>
        <Link className="public-button public-button-primary" to={user ? '/ships/new' : '/auth'}>
          {user ? 'Publish a Ship' : 'Start building'}
        </Link>
      </section>

      <PublicPageFooter />
    </article>
  )
}
