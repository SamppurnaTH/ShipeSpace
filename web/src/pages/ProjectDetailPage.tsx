import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { ShipCard } from '../components/ShipCard'
import { ShipCardSkeleton } from '../components/ShipCardSkeleton'
import { ProjectHeaderSkeleton } from '../components/common/Loading'
import { getProject, getProjectShips, getSentCollabs, likeShip, requestCollaboration, unlikeShip } from '../api/endpoints'
import { Icon } from '../components/Icon'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const { toast } = useToast()

  const [project, setProject] = useState<any>(null)
  const [ships, setShips] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [shipsLoading, setShipsLoading] = useState(true)
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())
  const [collaborationStatus, setCollaborationStatus] = useState<string | null>(null)
  const [collaborationPending, setCollaborationPending] = useState(false)

  useEffect(() => {
    if (!id) return
    Promise.all([
      getProject(id).then(r => setProject(r.data)),
      getProjectShips(id).then(r => setShips(r.data.content ?? [])),
    ])
      .catch(() => toast('Could not load project', 'error'))
      .finally(() => { setLoading(false); setShipsLoading(false) })
  }, [id])

  useEffect(() => {
    if (!id || !user) return
    getSentCollabs(0, 50)
      .then(response => {
        const list = response.data.content ?? response.data ?? []
        const existing = list.find((item: any) => item.projectId === id)
        setCollaborationStatus(existing?.status ?? null)
      })
      .catch(() => {})
  }, [id, user])

  const toggleLike = async (shipId: string) => {
    if (!user) return
    if (user.banned) {
      toast('Writes are blocked for this account.', 'error')
      return
    }
    const liked = likedIds.has(shipId)
    setLikedIds(p => {
      const n = new Set(p)
      if (liked) {
        n.delete(shipId)
      } else {
        n.add(shipId)
      }
      return n
    })
    setShips(p => p.map(s => s.id === shipId ? { ...s, likeCount: s.likeCount + (liked ? -1 : 1) } : s))
    try { await (liked ? unlikeShip(shipId) : likeShip(shipId)) }
    catch {
      setLikedIds(p => {
        const n = new Set(p)
        if (liked) {
          n.add(shipId)
        } else {
          n.delete(shipId)
        }
        return n
      })
      setShips(p => p.map(s => s.id === shipId ? { ...s, likeCount: s.likeCount + (liked ? 1 : -1) } : s))
    }
  }

  const handleRequestCollaboration = async () => {
    if (!id || !user) {
      toast('Sign in to request collaboration.', 'error')
      return
    }
    if (user.banned) {
      toast('Writes are blocked for this account.', 'error')
      return
    }
    setCollaborationPending(true)
    try {
      const response = await requestCollaboration(id)
      setCollaborationStatus(response.data.status ?? 'PENDING')
      toast(response.data.status === 'PENDING' ? 'Collaboration request sent' : 'Collaboration request already exists', 'success')
    } catch (err: any) {
      const message = err?.response?.data?.message ?? 'Could not request collaboration.'
      toast(message, 'error')
    } finally {
      setCollaborationPending(false)
    }
  }

  if (loading) return (
    <div style={{ maxWidth: 800, margin: '0 auto' }} aria-busy="true">
      <ProjectHeaderSkeleton />
      <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-muted)', marginBottom: 'var(--space-3)', fontFamily: 'var(--font-mono)' }}>
        SHIP HISTORY
      </div>
      {Array.from({ length: 3 }).map((_, i) => <ShipCardSkeleton key={i} />)}
    </div>
  )
  if (!project) return (
    <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
      <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="search" size={40} /></div>
      <div style={{ fontWeight: 600 }}>Project not found</div>
    </div>
  )

  const isWriteBlocked = Boolean(user?.banned)
  const isOwner = Boolean(user && user.id === project.userId)
  const canRequestCollaboration = Boolean(user && !isOwner && !isWriteBlocked && !collaborationStatus)

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      {/* Project header */}
      <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', marginBottom: 'var(--space-5)' }}>
        <h1 style={{ fontSize: 'var(--text-2xl)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>{project.name}</h1>
        <p style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-secondary)', lineHeight: 1.6, marginBottom: 'var(--space-4)' }}>
          {project.description}
        </p>

        {project.techTags?.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 'var(--space-4)' }}>
            {project.techTags.map((t: string) => (
              <span key={t} style={{
                fontSize: 'var(--text-xs)', padding: '3px 10px',
                background: 'var(--color-brand-light)', color: 'var(--color-brand-dim)',
                borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-mono)',
              }}>{t}</span>
            ))}
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-4)', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', gap: 'var(--space-4)', fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
            <span>{project.shipCount ?? 0} ships</span>
            {project.userId && (
              <Link to={`/profile/${project.userId}`} style={{ color: 'var(--color-brand-dim)' }}>
                View owner
              </Link>
            )}
          </div>

          {user ? (
            isOwner ? (
              <span style={collaborationPillStyle}>Your project</span>
            ) : (
              <button
                type="button"
                disabled={!canRequestCollaboration || collaborationPending}
                onClick={handleRequestCollaboration}
                style={collaborationButtonStyle(!canRequestCollaboration || collaborationPending)}
              >
                {collaborationPending
                  ? 'Sending...'
                  : collaborationStatus
                    ? `Collaboration ${collaborationStatus.toLowerCase()}`
                    : 'Request collaboration'}
              </button>
            )
          ) : (
            <Link to="/auth?mode=signin" style={collaborationLinkStyle}>
              Sign in to collaborate
            </Link>
          )}
        </div>
      </div>

      {/* Ship history */}
      <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-muted)', marginBottom: 'var(--space-3)', fontFamily: 'var(--font-mono)' }}>
        SHIP HISTORY
      </div>

      {isWriteBlocked && (
        <div className="blocked-write-banner" role="status">
          Likes and project write actions are unavailable for this account. Public project browsing remains available.
        </div>
      )}

      {shipsLoading && Array.from({ length: 3 }).map((_, i) => <ShipCardSkeleton key={i} />)}

      {!shipsLoading && ships.length === 0 && (
        <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)', background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)' }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="ship" size={32} /></div>
          <div style={{ fontSize: 'var(--text-sm)' }}>No ships yet for this project.</div>
        </div>
      )}

      {ships.map(s => (
        <ShipCard
          key={s.id}
          ship={s}
          user={s.user}
          project={project}
          liked={likedIds.has(s.id)}
          canLike={!!user && !isWriteBlocked}
          onLike={() => toggleLike(s.id)}
          isOwner={isOwner}
        />
      ))}
    </div>
  )
}

const collaborationPillStyle: React.CSSProperties = {
  padding: '8px 14px',
  borderRadius: 'var(--radius-full)',
  background: 'var(--color-surface-2)',
  color: 'var(--color-text-muted)',
  fontSize: 'var(--text-sm)',
  fontWeight: 700,
}

const collaborationLinkStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  padding: '9px 16px',
  borderRadius: 'var(--radius-md)',
  background: 'var(--color-brand)',
  color: '#fff',
  textDecoration: 'none',
  fontSize: 'var(--text-sm)',
  fontWeight: 700,
}

function collaborationButtonStyle(disabled: boolean): React.CSSProperties {
  return {
    padding: '9px 16px',
    borderRadius: 'var(--radius-md)',
    border: disabled ? '1px solid var(--color-border)' : 'none',
    background: disabled ? 'var(--color-surface-2)' : 'var(--color-brand)',
    color: disabled ? 'var(--color-text-muted)' : '#fff',
    cursor: disabled ? 'not-allowed' : 'pointer',
    fontSize: 'var(--text-sm)',
    fontWeight: 700,
    fontFamily: 'inherit',
  }
}
