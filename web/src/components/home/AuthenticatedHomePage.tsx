import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { deleteShip, getMyProjects } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { useFeed, type FeedItem, type FeedType } from '../../hooks/useFeed'
import { ShipCard } from '../ShipCard'
import { ProjectModal } from '../ProjectModal'
import { FeedSkeleton, ProjectCardSkeleton } from '../common/Loading'
import { HomeEmptyState } from './HomeEmptyState'

interface Project {
  id: string
  name: string
  description?: string
  techTags?: string[]
  shipCount?: number
  lastShipAt?: string
  createdAt?: string
}

type ProjectState = 'loading' | 'done' | 'empty' | 'error'
type HomeFeedFilter = Extract<FeedType, 'personalized' | 'latest' | 'following' | 'trending'>

const FEED_FILTERS: { label: string; value: HomeFeedFilter }[] = [
  { label: 'For You', value: 'personalized' },
  { label: 'Latest', value: 'latest' },
  { label: 'Following', value: 'following' },
  { label: 'Trending', value: 'trending' },
]

export function AuthenticatedHomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState('')
  const [projectState, setProjectState] = useState<ProjectState>('loading')
  const [showProjectModal, setShowProjectModal] = useState(false)
  const [feedType, setFeedType] = useState<HomeFeedFilter>('personalized')
  const loaderRef = useRef<HTMLDivElement>(null)

  const { items, setItems, status, hasMore, systemState, likedIds, loadMore, toggleLike } =
    useFeed(!!user, feedType)

  const isBanned = Boolean(user?.banned)
  const recentProjects = useMemo(() => projects.slice(0, 4), [projects])
  const totalShips = useMemo(
    () => projects.reduce((sum, project) => sum + (project.shipCount ?? 0), 0),
    [projects],
  )

  const activeProjects = useMemo(
    () => projects.filter(project => (project.shipCount ?? 0) > 0).length,
    [projects],
  )

  const uniqueBuilders = useMemo(() => {
    const seen = new Set<string>()
    return items
      .map(item => item.user)
      .filter((builder): builder is NonNullable<FeedItem['user']> => {
        if (!builder?.id || seen.has(builder.id) || builder.id === user?.id) return false
        seen.add(builder.id)
        return true
      })
      .slice(0, 4)
  }, [items, user?.id])

  const trendingProjects = useMemo(() => {
    const seen = new Set<string>()
    return items
      .map(item => item.project)
      .filter((project): project is NonNullable<FeedItem['project']> => {
        if (!project?.id || seen.has(project.id)) return false
        seen.add(project.id)
        return true
      })
      .slice(0, 4)
  }, [items])

  useEffect(() => {
    const el = loaderRef.current
    if (!el) return
    const obs = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadMore()
    }, { threshold: 0.1 })
    obs.observe(el)
    return () => obs.disconnect()
  }, [loadMore])

  const fetchProjects = () => {
    setProjectState('loading')
    getMyProjects()
      .then(res => {
        const list = (Array.isArray(res.data) ? res.data : res.data.content ?? []) as Project[]
        setProjects(list)
        setSelectedProjectId(current => current || list[0]?.id || '')
        setProjectState(list.length > 0 ? 'done' : 'empty')
      })
      .catch(err => {
        const code = err?.response?.data?.code
        setProjectState(err?.response?.status === 403 && code === 'BANNED_USER' ? 'empty' : 'error')
      })
  }

  useEffect(() => { fetchProjects() }, [])

  const handleDelete = async (shipId: string) => {
    if (!confirm('Delete this ship? This cannot be undone.')) return
    try {
      await deleteShip(shipId)
      setItems(prev => prev.filter(item => item.ship.id !== shipId))
    } catch {
      window.alert('Could not delete ship')
    }
  }

  const openShipComposer = () => {
    if (isBanned) return
    navigate('/ships/new')
  }

  const greeting = getGreeting()

  return (
    <div className="builder-home">
      <aside className="builder-home-left" aria-label="Builder context">
        <BuilderIdentityCard
          user={user}
          projectsCount={projects.length}
          shipsCount={totalShips}
        />
      </aside>

      <main className="builder-home-feed" aria-label="Build feed">
        <HomeGreeting greeting={greeting} username={user?.username} />

        {isBanned && (
          <HomeEmptyState
            tone="warning"
            title="Write actions are blocked"
            body="You can still browse and read public pages. Creating projects, publishing ships, likes, comments, follows, and uploads are unavailable for this account."
            action={<Link className="home-link" to="/trending">Browse public ships</Link>}
          />
        )}

        <ShipComposer
          avatarUrl={user?.avatarUrl}
          username={user?.username}
          projects={projects}
          projectState={projectState}
          selectedProjectId={selectedProjectId}
          onSelectProject={setSelectedProjectId}
          onCreateProject={() => setShowProjectModal(true)}
          onPublish={openShipComposer}
          disabled={isBanned}
        />

        <ProjectMomentum
          projects={recentProjects}
          projectState={projectState}
          isBanned={isBanned}
          onCreateProject={() => setShowProjectModal(true)}
          onAddShip={openShipComposer}
        />

        <section className="builder-feed-card" aria-labelledby="home-feed-title">
          <div className="builder-feed-header">
            <div>
              <p className="builder-kicker">Build feed</p>
              <h2 id="home-feed-title">Ships from the builder network</h2>
            </div>
            <nav className="builder-feed-filters" aria-label="Feed filters">
              {FEED_FILTERS.map(filter => (
                <button
                  key={filter.value}
                  type="button"
                  className={feedType === filter.value ? 'is-active' : ''}
                  aria-current={feedType === filter.value ? 'page' : undefined}
                  onClick={() => setFeedType(filter.value)}
                >
                  {filter.label}
                </button>
              ))}
            </nav>
          </div>

          {systemState !== 'normal' && (
            <div className="builder-feed-note" role="status">
              Feed is running in {systemState} mode. Showing the best available ships.
            </div>
          )}

          {status === 'loading' && items.length === 0 && <FeedSkeleton count={3} />}

          {status === 'done' && items.length === 0 && (
            <HomeEmptyState
              title="Your builder network starts here"
              body="Follow builders, explore projects, or publish your first ship to start shaping your feed."
              action={(
                <div className="builder-empty-actions">
                  <Link className="home-btn home-btn-secondary" to="/search">Explore builders</Link>
                  {!isBanned && <button className="home-btn home-btn-primary" onClick={openShipComposer}>Publish a ship</button>}
                </div>
              )}
            />
          )}

          {status === 'error' && items.length === 0 && (
            <HomeEmptyState
              tone="error"
              title="Feed could not load"
              body="Your projects are still available, but the builder feed is temporarily unreachable."
              action={<Link className="home-link" to="/trending">Open trending ships</Link>}
            />
          )}

          {items.map(item => (
            <ShipCard
              key={item.ship.id}
              ship={item.ship}
              user={item.user}
              project={item.project}
              liked={likedIds.has(item.ship.id)}
              canLike={!!user && !isBanned}
              onLike={() => toggleLike(item)}
              isOwner={!!user && user.id === item.user?.id}
              onDelete={() => handleDelete(item.ship.id)}
            />
          ))}

          <div ref={loaderRef} className="builder-feed-sentinel">
            {status === 'loading' && items.length > 0 && <span className="inline-loading">Loading more ships...</span>}
            {!hasMore && items.length > 0 && <span>End of feed</span>}
          </div>
        </section>
      </main>

      <aside className="builder-home-right" aria-label="Builder intelligence">
        <BuilderInsightsRail
          projectState={projectState}
          projectsCount={projects.length}
          activeProjects={activeProjects}
          shipsCount={totalShips}
          builders={uniqueBuilders}
          trendingProjects={trendingProjects}
        />
      </aside>

      <ProjectModal
        open={showProjectModal}
        onClose={() => setShowProjectModal(false)}
        onSuccess={fetchProjects}
      />
    </div>
  )
}

function BuilderIdentityCard({
  user,
  projectsCount,
  shipsCount,
}: {
  user: ReturnType<typeof useAuth>['user']
  projectsCount: number
  shipsCount: number
}) {
  if (!user) return null
  return (
    <section className="builder-identity-card">
      <img src={user.avatarUrl} alt={`${user.username} avatar`} />
      <h2>{user.username}</h2>
      <p>Builder shipping public project progress.</p>
      <dl className="builder-metrics">
        <div><dt>ShipScore</dt><dd>{user.shipScore?.toLocaleString() ?? '0'}</dd></div>
        <div><dt>Rank</dt><dd>{formatRank(user.rank)} · Lv.{user.level}</dd></div>
        <div><dt>Projects</dt><dd>{projectsCount}</dd></div>
        <div><dt>Ships</dt><dd>{shipsCount}</dd></div>
      </dl>
      <nav className="builder-side-nav" aria-label="Builder shortcuts">
        <Link to="/projects">My Projects</Link>
        <Link to={`/profile/${user.username}`}>My Ships</Link>
        <Link to="/collaborations">My Network</Link>
        <Link to="/search">Find Builders</Link>
      </nav>
      <Link className="builder-profile-link" to={`/profile/${user.username}`}>View public profile</Link>
    </section>
  )
}

function HomeGreeting({ greeting, username }: { greeting: string; username?: string }) {
  return (
    <section className="builder-greeting" aria-labelledby="builder-greeting-title">
      <p className="builder-kicker">{greeting}</p>
      <h1 id="builder-greeting-title">{username ? `${username}, what are you building right now?` : 'What are you building right now?'}</h1>
    </section>
  )
}

function ShipComposer({
  avatarUrl,
  username,
  projects,
  projectState,
  selectedProjectId,
  onSelectProject,
  onCreateProject,
  onPublish,
  disabled,
}: {
  avatarUrl?: string
  username?: string
  projects: Project[]
  projectState: ProjectState
  selectedProjectId: string
  onSelectProject: (id: string) => void
  onCreateProject: () => void
  onPublish: () => void
  disabled?: boolean
}) {
  return (
    <section className="ship-composer" aria-labelledby="ship-composer-title">
      <div className="ship-composer-prompt">
        {avatarUrl && <img src={avatarUrl} alt={username ? `${username} avatar` : ''} />}
        <button type="button" onClick={onPublish} disabled={disabled}>
          <span id="ship-composer-title">What are you shipping?</span>
        </button>
      </div>
      <div className="ship-composer-controls">
        {projectState === 'done' && projects.length > 0 ? (
          <label>
            <span>Project</span>
            <select value={selectedProjectId} onChange={event => onSelectProject(event.target.value)}>
              {projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
            </select>
          </label>
        ) : (
          <button type="button" className="ship-composer-project-cta" onClick={onCreateProject} disabled={disabled}>
            Create a project first
          </button>
        )}
        <div className="ship-composer-types" aria-label="Ship types">
          {['Progress update', 'Milestone', 'Screenshot', 'Demo', 'Release'].map(type => (
            <span key={type}>{type}</span>
          ))}
        </div>
        <button type="button" className="home-btn home-btn-primary" onClick={onPublish} disabled={disabled || projectState !== 'done'}>
          Publish Ship
        </button>
      </div>
    </section>
  )
}

function ProjectMomentum({
  projects,
  projectState,
  isBanned,
  onCreateProject,
  onAddShip,
}: {
  projects: Project[]
  projectState: ProjectState
  isBanned: boolean
  onCreateProject: () => void
  onAddShip: () => void
}) {
  return (
    <section className="project-momentum-strip" aria-labelledby="project-momentum-title" aria-busy={projectState === 'loading'}>
      <div className="project-momentum-head">
        <div>
          <p className="builder-kicker">Continue building</p>
          <h2 id="project-momentum-title">Your project momentum</h2>
        </div>
        {!isBanned && <button className="home-link-button" onClick={onCreateProject}>New project</button>}
      </div>

      {projectState === 'loading' && (
        <div className="project-momentum-grid">
          <ProjectCardSkeleton />
          <ProjectCardSkeleton />
        </div>
      )}

      {projectState === 'empty' && (
        <HomeEmptyState
          title="Start your first project"
          body="Turn your progress into proof of work by giving every ship a project timeline."
          action={!isBanned && <button className="home-btn home-btn-primary" onClick={onCreateProject}>Create project</button>}
        />
      )}

      {projectState === 'error' && (
        <HomeEmptyState tone="error" title="Projects could not load" body="Project momentum is temporarily unavailable." />
      )}

      {projectState === 'done' && (
        <div className="project-momentum-grid">
          {projects.map(project => (
            <article key={project.id} className="project-momentum-card">
              <Link to={`/projects/${project.id}`}>{project.name}</Link>
              <p>{project.description || 'Project timeline ready for your next ship.'}</p>
              <footer>
                <span>{project.shipCount ?? 0} ships</span>
                {!isBanned && <button type="button" onClick={onAddShip}>Add ship</button>}
              </footer>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function BuilderInsightsRail({
  projectState,
  projectsCount,
  activeProjects,
  shipsCount,
  builders,
  trendingProjects,
}: {
  projectState: ProjectState
  projectsCount: number
  activeProjects: number
  shipsCount: number
  builders: NonNullable<FeedItem['user']>[]
  trendingProjects: NonNullable<FeedItem['project']>[]
}) {
  return (
    <div className="builder-insights">
      <section className="builder-insight-card">
        <h2>Your momentum</h2>
        {projectState === 'loading' ? (
          <ProjectCardSkeleton />
        ) : (
          <dl>
            <div><dt>Total ships</dt><dd>{shipsCount}</dd></div>
            <div><dt>Active projects</dt><dd>{activeProjects}</dd></div>
            <div><dt>Projects</dt><dd>{projectsCount}</dd></div>
          </dl>
        )}
        <Link to="/projects">Keep building</Link>
      </section>

      <section className="builder-insight-card">
        <h2>Builders to watch</h2>
        {builders.length > 0 ? builders.map(builder => (
          <Link key={builder.id} className="builder-watch-row" to={`/profile/${builder.username}`}>
            <img src={builder.avatarUrl} alt={`${builder.username} avatar`} />
            <span>
              <strong>{builder.username}</strong>
              <small>{builder.rank ? `${formatRank(builder.rank)} · Lv.${builder.level ?? 1}` : 'Shipping publicly'}</small>
            </span>
          </Link>
        )) : (
          <p className="builder-muted">Explore ships to discover builders worth following.</p>
        )}
      </section>

      <section className="builder-insight-card">
        <h2>Trending projects</h2>
        {trendingProjects.length > 0 ? trendingProjects.map(project => (
          <Link key={project.id} className="builder-project-row" to={`/projects/${project.id}`}>
            <strong>{project.name}</strong>
            {project.techTags?.length > 0 && <small>{project.techTags.slice(0, 2).join(' · ')}</small>}
          </Link>
        )) : (
          <p className="builder-muted">Trending project context appears as the feed loads.</p>
        )}
      </section>
    </div>
  )
}

function formatRank(rank?: string) {
  if (!rank) return 'Builder'
  return rank
    .toLowerCase()
    .split('_')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}
