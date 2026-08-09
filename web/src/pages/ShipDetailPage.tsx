import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getShip, getComments, likeShip, unlikeShip, addComment, deleteShip } from '../api/endpoints'
import { Badge } from '../components/Badge'
import { CommentThread } from '../components/CommentThread'
import { Modal } from '../components/Modal'
import { toastSuccess, toastError } from '../components/Toast'
import { ButtonLoader, ShipCardSkeleton } from '../components/common/Loading'
import { Icon } from '../components/Icon'

interface ShipDetail {
  id: string
  content: string
  likeCount: number
  commentCount: number
  createdAt: string
  editWindowOpen?: boolean
  editWindowExpires?: string
  images?: string[]
  externalLinks?: { url: string; type: string }[]
  user?: { id: string; username: string; avatarUrl: string; rank?: string; level?: number }
  project?: { id: string; name: string }
  userId?: string
}

export function ShipDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const [ship, setShip] = useState<ShipDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [liked, setLiked] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [comments, setComments] = useState<any[]>([])
  const [commentsLoading, setCommentsLoading] = useState(true)
  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const [editCountdown, setEditCountdown] = useState(0)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getShip(id)
      .then(res => { setShip(res.data); setLoading(false) })
      .catch(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    setCommentsLoading(true)
    getComments(id, 0, 50)
      .then(res => { setComments(res.data.content ?? []); setCommentsLoading(false) })
      .catch(() => setCommentsLoading(false))
  }, [id])

  // Edit window countdown
  useEffect(() => {
    if (!ship?.editWindowExpires) return
    const update = () => {
      const remaining = Math.max(0, Math.floor((new Date(ship.editWindowExpires!).getTime() - Date.now()) / 1000))
      setEditCountdown(remaining)
    }
    update()
    const interval = setInterval(update, 1000)
    return () => clearInterval(interval)
  }, [ship?.editWindowExpires])

  const handleLike = async () => {
    if (!ship || !me) return
    if (me.banned) {
      toastError('Writes are blocked for this account.')
      return
    }
    const was = liked
    setLiked(!was)
    setShip(prev => prev ? { ...prev, likeCount: prev.likeCount + (was ? -1 : 1) } : prev)
    try {
      await (was ? unlikeShip(ship.id) : likeShip(ship.id))
    } catch {
      setLiked(was)
      setShip(prev => prev ? { ...prev, likeCount: prev.likeCount + (was ? 1 : -1) } : prev)
    }
  }

  const handleComment = async () => {
    if (!id || !commentText.trim()) return
    if (me?.banned) {
      toastError('Writes are blocked for this account.')
      return
    }
    setSubmitting(true)
    try {
      const res = await addComment(id, commentText.trim())
      setComments(prev => [res.data, ...prev])
      setCommentText('')
      setShip(prev => prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev)
      toastSuccess('Comment posted')
    } catch {
      toastError('Failed to post comment')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    if (!ship) return
    try {
      await deleteShip(ship.id)
      toastSuccess('Ship deleted')
      navigate('/feed')
    } catch {
      toastError('Failed to delete ship')
    }
    setShowDeleteModal(false)
  }

  const timeAgo = (iso: string) => {
    const diff = Date.now() - new Date(iso).getTime()
    const m = Math.floor(diff / 60000)
    if (m < 1) return 'just now'
    if (m < 60) return `${m}m ago`
    const h = Math.floor(m / 60)
    if (h < 24) return `${h}h ago`
    return `${Math.floor(h / 24)}d ago`
  }

  const fmtCountdown = (s: number) => {
    const mm = Math.floor(s / 60).toString().padStart(2, '0')
    const ss = (s % 60).toString().padStart(2, '0')
    return `${mm}:${ss}`
  }

  if (loading) {
    return (
      <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-4)' }} aria-busy="true">
        <ShipCardSkeleton media />
        <div className="skeleton-card">
          <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-muted)', marginBottom: 'var(--space-4)' }}>
            COMMENTS
          </div>
          <ShipCardSkeleton />
        </div>
      </div>
    )
  }

  if (!ship) {
    return (
      <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
        <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="search" size={48} /></div>
        <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>Ship not found</div>
        <Link to="/feed" style={{ color: 'var(--color-brand)', fontSize: 'var(--text-sm)', marginTop: 'var(--space-3)', display: 'inline-block' }}>
          Back to feed
        </Link>
      </div>
    )
  }

  const isOwner = me && (ship.userId === me.id || ship.user?.username === me.username)
  const isWriteBlocked = Boolean(me?.banned)

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-4)' }}>
      {/* Back button */}
      <button
        onClick={() => navigate(-1)}
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)',
          fontFamily: 'inherit', marginBottom: 'var(--space-4)',
          padding: '4px 0',
        }}
      >
        <Icon name="chevronLeft" size={16} /> Back
      </button>

      {/* Ship card */}
      <article style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-6)',
        marginBottom: 'var(--space-4)',
      }}>
        {/* Author header */}
        <div style={{ display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-4)', alignItems: 'center' }}>
          {ship.user?.avatarUrl ? (
            <Link to={`/profile/${ship.user.username}`}>
              <img src={ship.user.avatarUrl} alt={ship.user.username}
                style={{ width: 48, height: 48, borderRadius: '50%', objectFit: 'cover' }} />
            </Link>
          ) : (
            <div style={{
              width: 48, height: 48, borderRadius: '50%',
              background: 'var(--color-brand-light)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 'var(--text-lg)', fontWeight: 700, color: 'var(--color-brand)',
            }}>?</div>
          )}
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
              <Link to={`/profile/${ship.user?.username}`}
                style={{ fontWeight: 600, fontSize: 'var(--text-base)', color: 'var(--color-text-primary)', textDecoration: 'none' }}>
                @{ship.user?.username ?? 'unknown'}
              </Link>
              {ship.user?.rank && (
                <Badge variant="brand">{ship.user.rank} · Lv.{ship.user.level}</Badge>
              )}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginTop: 2 }}>
              {ship.project && (
                <Link to={`/projects/${ship.project.id}`}
                  style={{ fontSize: 'var(--text-sm)', color: 'var(--color-brand)', textDecoration: 'none' }}>
                  {ship.project.name}
                </Link>
              )}
              <time style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                {timeAgo(ship.createdAt)}
              </time>
            </div>
          </div>
        </div>

        {/* Full content */}
        <p style={{
          fontSize: 'var(--text-base)', lineHeight: 'var(--leading-normal)',
          color: 'var(--color-text-primary)', marginBottom: 'var(--space-4)',
          whiteSpace: 'pre-wrap', wordBreak: 'break-word',
        }}>
          {ship.content}
        </p>

        {/* Images */}
        {ship.images && ship.images.length > 0 && (
          <div style={{
            display: 'grid',
            gridTemplateColumns: ship.images.length === 1 ? '1fr' : '1fr 1fr',
            gap: 'var(--space-2)',
            marginBottom: 'var(--space-4)',
            borderRadius: 'var(--radius-md)',
            overflow: 'hidden',
          }}>
            {ship.images.slice(0, 4).map((img, i) => (
              <img key={i} src={img} alt={`Ship image ${i + 1}`}
                style={{ width: '100%', height: 200, objectFit: 'cover', borderRadius: 'var(--radius-sm)' }} />
            ))}
          </div>
        )}

        {/* External links */}
        {ship.externalLinks && ship.externalLinks.length > 0 && (
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', marginBottom: 'var(--space-4)' }}>
            {ship.externalLinks.map((link, i) => (
              <a key={i} href={link.url} target="_blank" rel="noopener noreferrer"
                style={{
                  display: 'inline-flex', alignItems: 'center', gap: 4,
                  padding: '4px 12px', borderRadius: 'var(--radius-full)',
                  border: '1px solid var(--color-border)',
                  fontSize: 'var(--text-xs)', color: 'var(--color-brand)',
                  textDecoration: 'none',
                  transition: 'all var(--transition-fast)',
                }}
              >
                <Icon name="externalLink" size={14} /> {link.type || 'Link'}
              </a>
            ))}
          </div>
        )}

        {/* Actions bar */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 'var(--space-4)',
          borderTop: '1px solid var(--color-border)',
          paddingTop: 'var(--space-3)',
        }}>
          <button onClick={handleLike} disabled={!me || isWriteBlocked} style={{
            display: 'flex', alignItems: 'center', gap: 'var(--space-1)',
            background: liked ? 'var(--color-like-bg)' : 'none',
            border: 'none', borderRadius: 'var(--radius-sm)',
            padding: '6px 12px', cursor: me && !isWriteBlocked ? 'pointer' : 'default',
            color: liked ? 'var(--color-like)' : 'var(--color-text-muted)',
            fontSize: 'var(--text-sm)', fontFamily: 'inherit',
            transition: 'all var(--transition-fast)',
          }}>
            <Icon name="thumb" size={16} />
            {ship.likeCount}
          </button>

          <span style={{
            display: 'flex', alignItems: 'center', gap: 'var(--space-1)',
            fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)',
          }}>
            <Icon name="comment" size={16} /> {ship.commentCount}
          </span>

          {/* Edit window countdown */}
          {isOwner && editCountdown > 0 && (
            <span style={{
              marginLeft: 'auto',
              display: 'flex', alignItems: 'center', gap: 4,
              padding: '4px 10px', borderRadius: 'var(--radius-full)',
              background: 'var(--color-warning-bg)',
              border: '1px solid var(--color-warning)',
              fontSize: 'var(--text-xs)', fontWeight: 600,
              color: 'var(--color-warning)',
            }}>
              <Icon name="edit" size={14} /> Edit closes in {fmtCountdown(editCountdown)}
            </span>
          )}

          {/* Owner actions */}
          {isOwner && (
            <button onClick={() => setShowDeleteModal(true)} style={{
              marginLeft: editCountdown > 0 ? 0 : 'auto',
              background: 'none', border: 'none', cursor: 'pointer',
              color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)',
              fontFamily: 'inherit', padding: '4px 8px',
              borderRadius: 'var(--radius-sm)',
              transition: 'all var(--transition-fast)',
            }}
              onMouseEnter={e => { e.currentTarget.style.color = 'var(--color-error)'; e.currentTarget.style.background = 'var(--color-error-bg)' }}
              onMouseLeave={e => { e.currentTarget.style.color = 'var(--color-text-muted)'; e.currentTarget.style.background = 'none' }}
            >
              <Icon name="trash" size={14} /> Delete
            </button>
          )}
        </div>
      </article>

      {/* Comments section */}
      <div style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-5)',
      }}>
        <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)' }}>
          COMMENTS ({ship.commentCount})
        </div>

        {/* Comment compose */}
        {isWriteBlocked && (
          <div className="blocked-write-banner" role="status">
            Writes are blocked for this account. You can still read public ships and comments.
          </div>
        )}

        {me && !isWriteBlocked && (
          <div style={{
            display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-5)',
            padding: 'var(--space-3)',
            background: 'var(--color-surface-2)',
            borderRadius: 'var(--radius-md)',
          }}>
            <img src={me.avatarUrl} alt={me.username}
              style={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <textarea
                value={commentText}
                onChange={e => setCommentText(e.target.value)}
                placeholder="Write a comment..."
                maxLength={1000}
                rows={2}
                style={{
                  width: '100%', resize: 'vertical',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: 'var(--space-3)',
                  fontSize: 'var(--text-sm)',
                  fontFamily: 'inherit',
                  background: 'var(--color-surface)',
                  color: 'var(--color-text-primary)',
                  outline: 'none',
                  transition: 'border-color var(--transition-fast)',
                }}
                onFocus={e => e.currentTarget.style.borderColor = 'var(--color-brand)'}
                onBlur={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-2)' }}>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  {commentText.length}/1000
                </span>
                <button
                  onClick={handleComment}
                  disabled={!commentText.trim() || submitting}
                  style={{
                    padding: '6px 16px', borderRadius: 'var(--radius-md)',
                    background: commentText.trim() ? 'var(--color-brand)' : 'var(--color-surface-2)',
                    color: commentText.trim() ? '#fff' : 'var(--color-text-muted)',
                    border: 'none', cursor: commentText.trim() ? 'pointer' : 'default',
                    fontSize: 'var(--text-sm)', fontWeight: 600, fontFamily: 'inherit',
                    transition: 'all var(--transition-fast)',
                  }}
                >
                  {submitting ? <ButtonLoader label="Posting..." /> : 'Post'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Comments list */}
        {commentsLoading ? (
          <div aria-busy="true">
            <ShipCardSkeleton />
          </div>
        ) : comments.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 'var(--space-6)', color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
            No comments yet. Be the first to comment!
          </div>
        ) : (
          <CommentThread comments={comments} currentUserId={me?.id} onDelete={(commentId) => {
            setComments(prev => prev.filter(c => c.id !== commentId))
            setShip(prev => prev ? { ...prev, commentCount: prev.commentCount - 1 } : prev)
          }} />
        )}
      </div>

      {/* Delete confirmation modal */}
      <Modal open={showDeleteModal} onClose={() => setShowDeleteModal(false)} title="Delete this ship?" size="sm">
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-5)' }}>
          This action cannot be undone. The ship and all its engagement data will be permanently removed.
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'flex-end' }}>
          <button onClick={() => setShowDeleteModal(false)} style={{
            padding: '8px 16px', borderRadius: 'var(--radius-md)',
            border: '1px solid var(--color-border)', background: 'var(--color-surface)',
            cursor: 'pointer', fontSize: 'var(--text-sm)', fontFamily: 'inherit',
            color: 'var(--color-text-secondary)',
          }}>Cancel</button>
          <button onClick={handleDelete} style={{
            padding: '8px 16px', borderRadius: 'var(--radius-md)',
            border: 'none', background: 'var(--color-error)', color: '#fff',
            cursor: 'pointer', fontSize: 'var(--text-sm)', fontWeight: 600, fontFamily: 'inherit',
          }}>Delete</button>
        </div>
      </Modal>
    </div>
  )
}
