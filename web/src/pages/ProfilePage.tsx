import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useProfile } from '../hooks/useProfile'
import { ShipCard } from '../components/ShipCard'
import { ShipCardSkeleton } from '../components/ShipCardSkeleton'
import { BuilderCardSkeleton, ProjectCardSkeleton } from '../components/common/Loading'
import { getUserProjects, getUserShips, likeShip, unlikeShip } from '../api/endpoints'
import { Icon } from '../components/Icon'

const RANK_COLORS: Record<string, string> = {
  DOCKED: '#64748b',
  LAUNCHER: '#16a34a',
  ORBITER: '#2563eb',
  NAVIGATOR: '#7c3aed',
  COMMANDER: '#d97706',
  FLEET_ADMIRAL: '#dc2626',
}

export function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: me } = useAuth()
  const { profile, rankSummary, loading, error, followPending, toggleFollow } = useProfile(id)
  const [ships, setShips] = useState<any[]>([])
  const [projects, setProjects] = useState<any[]>([])
  const [shipsLoading, setShipsLoading] = useState(true)
  const [projectsLoading, setProjectsLoading] = useState(true)
  const [shipsError, setShipsError] = useState<string | null>(null)
  const [projectsError, setProjectsError] = useState<string | null>(null)
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())

  useEffect(() => {
    if (!id) return
    setProjectsLoading(true)
    setProjectsError(null)
    getUserProjects(id, 0, 6)
      .then(r => setProjects(r.data.content ?? []))
      .catch(() => setProjectsError('Could not load projects'))
      .finally(() => setProjectsLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    setShipsLoading(true)
    setShipsError(null)
    getUserShips(id)
      .then(r => setShips(r.data.content ?? []))
      .catch(() => setShipsError('Could not load ships'))
      .finally(() => setShipsLoading(false))
  }, [id])

  const toggleLike = async (shipId: string) => {
    if (!me || me.banned) return
    const liked = likedIds.has(shipId)
    setLikedIds(prev => {
      const next = new Set(prev)
      if (liked) {
        next.delete(shipId)
      } else {
        next.add(shipId)
      }
      return next
    })
    setShips(prev => prev.map(ship => (
      ship.id === shipId
        ? { ...ship, likeCount: Math.max(0, (ship.likeCount ?? 0) + (liked ? -1 : 1)) }
        : ship
    )))
    try {
      await (liked ? unlikeShip(shipId) : likeShip(shipId))
    } catch {
      setLikedIds(prev => {
        const next = new Set(prev)
        if (liked) {
          next.add(shipId)
        } else {
          next.delete(shipId)
        }
        return next
      })
      setShips(prev => prev.map(ship => (
        ship.id === shipId
          ? { ...ship, likeCount: Math.max(0, (ship.likeCount ?? 0) + (liked ? 1 : -1)) }
          : ship
      )))
    }
  }

  if (loading) {
    return (
      <div style={{ maxWidth: 760, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }} aria-busy="true">
        <BuilderCardSkeleton />
        <SectionLabel>PROJECTS</SectionLabel>
        <ProjectCardSkeleton />
        <ProjectCardSkeleton />
        <SectionLabel>SHIPS</SectionLabel>
        {Array.from({ length: 3 }).map((_, i) => <ShipCardSkeleton key={i} />)}
      </div>
    )
  }

  if (!profile) {
    return (
      <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
        <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="search" size={48} /></div>
        <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>{error ?? 'User not found'}</div>
      </div>
    )
  }

  const isOwnProfile = Boolean(me && me.id === profile.id)
  const isWriteBlocked = Boolean(me?.banned)
  const displayRank = rankSummary?.rank ?? profile.rank ?? 'DOCKED'
  const displayLevel = rankSummary?.level ?? profile.level ?? 1
  const displayScore = rankSummary?.shipScore ?? profile.shipScore ?? 0
  const rankColor = RANK_COLORS[displayRank] || 'var(--color-brand)'
  const progressPercent = rankSummary
    ? rankSummary.level >= 30 ? 100 : ((rankSummary.shipScore % 40) / 40) * 100
    : 0
  const pointsToNext = rankSummary?.nextLevelScore == null
    ? null
    : Math.max(0, rankSummary.nextLevelScore - rankSummary.shipScore)

  return (
    <div style={{ maxWidth: 760, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>
      <section style={cardStyle} aria-labelledby="profile-heading">
        <div style={{ display: 'flex', gap: 'var(--space-6)', alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <img
            src={profile.avatarUrl}
            alt={`${profile.username} avatar`}
            style={{ width: 84, height: 84, borderRadius: '50%', objectFit: 'cover', border: `3px solid ${rankColor}` }}
          />
          <div style={{ flex: 1, minWidth: 220 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginBottom: 'var(--space-2)', flexWrap: 'wrap' }}>
              <h1 id="profile-heading" style={{ fontSize: 'var(--text-2xl)', fontWeight: 700, margin: 0, overflowWrap: 'anywhere' }}>
                @{profile.username}
              </h1>
              <span style={rankBadgeStyle(rankColor)}>
                {displayRank} - Lv.{displayLevel}
              </span>
            </div>

            {profile.bio ? (
              <p style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-secondary)', margin: '0 0 var(--space-3) 0', lineHeight: 1.5, overflowWrap: 'anywhere' }}>
                {profile.bio}
              </p>
            ) : (
              <p style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-muted)', margin: '0 0 var(--space-3) 0' }}>
                {isOwnProfile ? 'Add a short builder bio so visitors understand what you are shipping.' : 'This builder has not added a bio yet.'}
              </p>
            )}

            <div style={{ display: 'flex', gap: 'var(--space-4)', fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', flexWrap: 'wrap' }}>
              {profile.location && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Icon name="location" size={14} /> {profile.location}</span>}
              {profile.websiteUrl && (
                <a href={profile.websiteUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--color-brand)', textDecoration: 'none', overflowWrap: 'anywhere' }}>
                  <Icon name="externalLink" size={14} /> {profile.websiteUrl.replace(/https?:\/\//, '')}
                </a>
              )}
            </div>
          </div>
        </div>

        <div style={statsStyle}>
          {[
            { label: 'Followers', value: profile.followerCount },
            { label: 'Following', value: profile.followingCount },
            { label: 'Projects', value: profile.projectCount },
            { label: 'ShipScore', value: displayScore },
          ].map(stat => (
            <div key={stat.label} style={{ textAlign: 'center', flex: 1, minWidth: 90 }}>
              <div style={{ fontSize: 'var(--text-xl)', fontWeight: 700, color: 'var(--color-text-primary)' }}>
                {(stat.value ?? 0).toLocaleString()}
              </div>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 2 }}>
                {stat.label}
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          {isOwnProfile ? (
            <Link to="/settings" style={secondaryButtonStyle}>
              <Icon name="edit" size={14} /> Edit Profile
            </Link>
          ) : (
            <>
              <button
                onClick={toggleFollow}
                disabled={isWriteBlocked || followPending}
                aria-pressed={Boolean(profile.isFollowing)}
                style={followButtonStyle(Boolean(profile.isFollowing), isWriteBlocked || followPending)}
              >
                {isWriteBlocked ? 'Writes blocked' : followPending ? 'Saving...' : profile.isFollowing ? 'Following' : 'Follow'}
              </button>
              {me && !isWriteBlocked && (
                <Link to="/collaborations" style={secondaryButtonStyle}>
                  <Icon name="network" size={14} /> Collaborate
                </Link>
              )}
            </>
          )}
        </div>
      </section>

      {error && <InlineAlert>{error}</InlineAlert>}

      {isWriteBlocked && (
        <div className="blocked-write-banner" role="status">
          Follow, like, and ship actions are unavailable for this account. Public browsing remains available.
        </div>
      )}

      {rankSummary ? (
        <section style={cardStyle} aria-labelledby="rank-heading">
          <div id="rank-heading" style={sectionHeadingStyle}>RANK PROGRESSION</div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-2)', gap: 'var(--space-3)' }}>
            <span style={{ fontSize: 'var(--text-sm)', fontWeight: 700, color: rankColor }}>
              Level {rankSummary.level} - {rankSummary.rank}
            </span>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              {rankSummary.shipScore.toLocaleString()} ShipScore
            </span>
          </div>
          <div style={{ height: 8, borderRadius: 'var(--radius-full)', background: 'var(--color-surface-2)', overflow: 'hidden' }}>
            <div style={{ height: '100%', borderRadius: 'var(--radius-full)', background: rankColor, width: `${progressPercent}%`, transition: 'width 500ms ease' }} />
          </div>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 'var(--space-2)' }}>
            {rankSummary.level >= 30 ? 'Maximum level reached' : `${pointsToNext} ShipScore to Level ${rankSummary.level + 1}`}
          </div>
        </section>
      ) : (
        <div role="status" style={{ ...cardStyle, color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
          Rank summary is unavailable right now. Profile identity and ships are still visible.
        </div>
      )}

      <SectionLabel>PROJECTS</SectionLabel>
      {projectsError && <InlineAlert>{projectsError}</InlineAlert>}
      {projectsLoading && Array.from({ length: 2 }).map((_, i) => <ProjectCardSkeleton key={i} />)}
      {!projectsLoading && projects.length === 0 && !projectsError && (
        <EmptyPanel
          icon="box"
          text={isOwnProfile ? "You haven't created a project yet." : 'No public projects yet.'}
          action={isOwnProfile && !isWriteBlocked ? { to: '/projects', label: 'Create project' } : undefined}
        />
      )}
      {!projectsLoading && projects.length > 0 && (
        <div style={{ display: 'grid', gap: 'var(--space-3)', marginBottom: 'var(--space-6)' }}>
          {projects.map(project => (
            <Link key={project.id} to={`/projects/${project.id}`} style={projectCardStyle}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 'var(--text-base)', fontWeight: 700, color: 'var(--color-text-primary)', overflowWrap: 'anywhere' }}>
                    {project.name}
                  </div>
                  {project.description && (
                    <p style={{ margin: 'var(--space-2) 0 0', color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)', lineHeight: 1.5, overflowWrap: 'anywhere' }}>
                      {project.description}
                    </p>
                  )}
                </div>
                <span style={{ color: 'var(--color-text-muted)', fontSize: 'var(--text-xs)', whiteSpace: 'nowrap' }}>
                  {project.shipCount ?? 0} ships
                </span>
              </div>
              {Array.isArray(project.techTags) && project.techTags.length > 0 && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 'var(--space-3)' }}>
                  {project.techTags.slice(0, 5).map((tag: string) => (
                    <span key={tag} style={tagStyle}>{tag}</span>
                  ))}
                </div>
              )}
            </Link>
          ))}
        </div>
      )}

      <SectionLabel>SHIPS</SectionLabel>
      {shipsError && <InlineAlert>{shipsError}</InlineAlert>}
      {shipsLoading && Array.from({ length: 3 }).map((_, i) => <ShipCardSkeleton key={i} />)}
      {!shipsLoading && ships.length === 0 && !shipsError && (
        <EmptyPanel
          icon="ship"
          text={isOwnProfile ? "You haven't shipped anything yet." : 'No ships yet.'}
          action={isOwnProfile && !isWriteBlocked ? { to: '/ships/new', label: 'Ship something' } : undefined}
        />
      )}
      {ships.map(ship => (
        <ShipCard
          key={ship.id}
          ship={ship}
          user={{ id: profile.id, username: profile.username, avatarUrl: profile.avatarUrl, rank: displayRank, level: displayLevel }}
          project={ship.project}
          liked={likedIds.has(ship.id)}
          canLike={!!me && !isWriteBlocked}
          onLike={() => toggleLike(ship.id)}
          isOwner={!!me && me.id === profile.id}
        />
      ))}
    </div>
  )
}

function SectionLabel({ children }: { children: string }) {
  return (
    <div style={{ fontSize: 'var(--text-sm)', fontWeight: 700, color: 'var(--color-text-muted)', margin: 'var(--space-6) 0 var(--space-3)', fontFamily: 'var(--font-mono)' }}>
      {children}
    </div>
  )
}

function InlineAlert({ children }: { children: string }) {
  return (
    <div role="alert" style={{ marginBottom: 'var(--space-4)', padding: 'var(--space-3)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-error)', background: 'var(--color-error-bg)', color: 'var(--color-error)', fontSize: 'var(--text-sm)' }}>
      {children}
    </div>
  )
}

function EmptyPanel({ icon, text, action }: { icon: 'box' | 'ship'; text: string; action?: { to: string; label: string } }) {
  return (
    <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)', padding: 'var(--space-8)', textAlign: 'center', color: 'var(--color-text-muted)', marginBottom: 'var(--space-6)' }}>
      <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name={icon} size={32} /></div>
      <div style={{ fontSize: 'var(--text-sm)' }}>{text}</div>
      {action && (
        <Link to={action.to} style={{ display: 'inline-block', marginTop: 'var(--space-4)', padding: '8px 20px', borderRadius: 'var(--radius-md)', background: 'var(--color-brand)', color: '#fff', fontSize: 'var(--text-sm)', fontWeight: 600, textDecoration: 'none' }}>
          {action.label}
        </Link>
      )}
    </div>
  )
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  borderRadius: 'var(--radius-lg)',
  border: '1px solid var(--color-border)',
  padding: 'var(--space-6)',
  marginBottom: 'var(--space-6)',
}

const statsStyle: React.CSSProperties = {
  display: 'flex',
  gap: 'var(--space-4)',
  margin: 'var(--space-6) 0',
  padding: 'var(--space-4) 0',
  borderTop: '1px solid var(--color-border)',
  borderBottom: '1px solid var(--color-border)',
  flexWrap: 'wrap',
}

const secondaryButtonStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '8px 20px',
  borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)',
  background: 'var(--color-surface)',
  color: 'var(--color-text-primary)',
  fontSize: 'var(--text-sm)',
  fontWeight: 600,
  textDecoration: 'none',
}

const projectCardStyle: React.CSSProperties = {
  display: 'block',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
  color: 'inherit',
  textDecoration: 'none',
}

const tagStyle: React.CSSProperties = {
  padding: '3px 8px',
  borderRadius: 'var(--radius-full)',
  background: 'var(--color-surface-2)',
  color: 'var(--color-text-muted)',
  fontSize: 'var(--text-xs)',
}

const sectionHeadingStyle: React.CSSProperties = {
  fontSize: 'var(--text-sm)',
  fontWeight: 700,
  marginBottom: 'var(--space-3)',
  color: 'var(--color-text-secondary)',
}

function rankBadgeStyle(rankColor: string): React.CSSProperties {
  return {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    padding: '3px 10px',
    borderRadius: 'var(--radius-full)',
    background: `${rankColor}18`,
    color: rankColor,
    fontSize: 'var(--text-xs)',
    fontWeight: 700,
    border: `1px solid ${rankColor}30`,
  }
}

function followButtonStyle(isFollowing: boolean, disabled: boolean): React.CSSProperties {
  return {
    padding: '8px 24px',
    borderRadius: 'var(--radius-md)',
    border: isFollowing ? '1px solid var(--color-border)' : 'none',
    background: disabled ? 'var(--color-surface-2)' : isFollowing ? 'var(--color-surface)' : 'var(--color-brand)',
    color: disabled ? 'var(--color-text-muted)' : isFollowing ? 'var(--color-text-primary)' : '#fff',
    fontSize: 'var(--text-sm)',
    fontWeight: 600,
    cursor: disabled ? 'not-allowed' : 'pointer',
    fontFamily: 'inherit',
    opacity: disabled ? 0.75 : 1,
  }
}
