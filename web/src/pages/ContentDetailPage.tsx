import { useEffect, useState, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { RankBadge } from '../components/RankBadge'
import { Spinner } from '../components/Spinner'
import {
  getShip, likeShip, unlikeShip,
  getComments, addComment, deleteComment, upvoteComment,
} from '../api/endpoints'

function timeAgo(iso: string) {
  const d = Date.now() - new Date(iso).getTime()
  const m = Math.floor(d / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

export function ContentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const { toast } = useToast()

  const [ship, setShip] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [liked, setLiked] = useState(false)

  const [comments, setComments] = useState<any[]>([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [posting, setPosting] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getShip(id)
      .then(r => setShip(r.data))
      .catch(() => toast('Could not load ship', 'error'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    setCommentsLoading(true)
    getComments(id)
      .then(r => setComments(r.data.content ?? []))
      .finally(() => setCommentsLoading(false))
  }, [id])

  const handleLike = async () => {
    if (!user || !ship) return
    const wasLiked = liked
    setLiked(!wasLiked)
    setShip((s: any) => ({ ...s, likeCount: s.likeCount + (wasLiked ? -1 : 1) }))
    try {
      await (wasLiked ? unlikeShip(ship.id) : likeShip(ship.id))
    } catch {
      setLiked(wasLiked)
      setShip((s: any) => ({ ...s, likeCount: s.likeCount + (wasLiked ? 1 : -1) }))
    }
  }

  const handleComment = async () => {
    if (!commentText.trim() || !id) return
    setPosting(true)
    try {
      const r = await addComment(id, commentText.trim())
      setComments(p => [r.data, ...p])
      setCommentText('')
      setShip((s: any) => ({ ...s, commentCount: s.commentCount + 1 }))
      toast('Comment posted', 'success')
    } catch {
      toast('Could not post comment', 'error')
    } finally { setPosting(false) }
  }

  const handleDeleteComment = async (commentId: string) => {
    try {
      await deleteComment(commentId)
      setComments(p => p.filter(c => c.id !== commentId))
      setShip((s: any) => ({ ...s, commentCount: Math.max(0, s.commentCount - 1) }))
    } catch { toast('Could not delete comment', 'error') }
  }

  const handleUpvote = async (commentId: string) => {
    try {
      const r = await upvoteComment(commentId)
      setComments(p => p.map(c => c.id === commentId ? { ...c, upvotes: r.data.upvoteCount } : c))
      if (r.data.qualifiedForScoring) toast('+1 ShipScore for quality comment!', 'success')
    } catch { toast('Could not upvote', 'error') }
  }

  if (loading) return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
      <Spinner size={28} />
    </div>
  )

  if (!ship) return (
    <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
      <div style={{ fontSize: 40, marginBottom: 'var(--space-3)' }}>🔍</div>
      <div style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>Ship not found</div>
    </div>
  )

  const u = ship.user
  const p = ship.project

  return (
    <div style={{ maxWidth: 'var(--feed-max-width)', margin: '0 auto' }}>
      {/* Ship card */}
      <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', marginBottom: 'var(--space-4)' }}>
        {/* Author */}
        <div style={{ display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-5)', alignItems: 'flex-start' }}>
          <Link to={u ? `/profile/${u.username}` : '#'}>
            <img src={u?.avatarUrl} alt={u?.username}
              style={{ width: 44, height: 44, borderRadius: '50%', objectFit: 'cover' }} />
          </Link>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
              <Link to={u ? `/profile/${u.username}` : '#'}
                style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>
                @{u?.username}
              </Link>
              {u?.rank && <RankBadge rank={u.rank} level={u.level} />}
            </div>
            {p && (
              <Link to={`/projects/${p.id}`}
                style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 2, display: 'block' }}>
                {p.name}
              </Link>
            )}
          </div>
          <time style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
            {timeAgo(ship.createdAt)}
          </time>
        </div>

        {/* Content */}
        <p style={{
          fontSize: 'var(--text-base)', lineHeight: 'var(--leading-normal)',
          color: 'var(--color-text-secondary)',
          whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          marginBottom: 'var(--space-5)',
        }}>{ship.content}</p>

        {/* Tech tags */}
        {p?.techTags?.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 'var(--space-4)' }}>
            {p.techTags.map((t: string) => (
              <span key={t} style={{
                fontSize: 'var(--text-xs)', padding: '2px 8px',
                background: 'var(--color-brand-light)', color: 'var(--color-brand-dim)',
                borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-mono)',
              }}>{t}</span>
            ))}
          </div>
        )}

        {/* Actions */}
        <div style={{ display: 'flex', gap: 'var(--space-4)', paddingTop: 'var(--space-4)', borderTop: '1px solid var(--color-border)' }}>
          <button onClick={handleLike} disabled={!user} style={{
            display: 'flex', alignItems: 'center', gap: 6,
            background: liked ? 'var(--color-like-bg)' : 'none',
            border: 'none', borderRadius: 'var(--radius-sm)',
            padding: '6px 12px', cursor: user ? 'pointer' : 'default',
            color: liked ? 'var(--color-like)' : 'var(--color-text-muted)',
            fontSize: 'var(--text-sm)', fontFamily: 'inherit',
            transition: 'all var(--transition-fast)',
          }}>
            <span>{liked ? '♥' : '♡'}</span>
            <span style={{ fontFamily: 'var(--font-mono)' }}>{ship.likeCount}</span>
          </button>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>
            <span>💬</span>
            <span style={{ fontFamily: 'var(--font-mono)' }}>{ship.commentCount}</span>
          </span>
        </div>
      </div>

      {/* Comment input */}
      {user && (
        <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-4)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
            <img src={user.avatarUrl} alt={user.username}
              style={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <textarea
                ref={textareaRef}
                value={commentText}
                onChange={e => setCommentText(e.target.value)}
                placeholder="Write a comment…"
                maxLength={1000}
                rows={3}
                style={{
                  width: '100%', resize: 'vertical',
                  background: 'var(--color-surface-2)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: 'var(--space-3)',
                  color: 'var(--color-text-primary)',
                  fontSize: 'var(--text-sm)',
                  outline: 'none',
                  transition: 'border-color var(--transition-fast)',
                }}
                onFocus={e => (e.target.style.borderColor = 'var(--color-brand)')}
                onBlur={e => (e.target.style.borderColor = 'var(--color-border)')}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-2)' }}>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
                  {commentText.length}/1000
                </span>
                <button onClick={handleComment} disabled={!commentText.trim() || posting} style={{
                  padding: '6px 16px', borderRadius: 'var(--radius-md)', border: 'none',
                  background: 'linear-gradient(135deg, var(--color-brand), var(--color-brand-dim))',
                  color: '#fff', fontSize: 'var(--text-sm)', fontWeight: 600,
                  cursor: commentText.trim() && !posting ? 'pointer' : 'not-allowed',
                  opacity: !commentText.trim() || posting ? 0.5 : 1,
                  fontFamily: 'inherit',
                }}>
                  {posting ? 'Posting…' : 'Post'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Comments list */}
      <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}>
        <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-muted)', marginBottom: 'var(--space-4)', fontFamily: 'var(--font-mono)' }}>
          COMMENTS ({ship.commentCount})
        </div>
        {commentsLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-6)' }}><Spinner size={20} /></div>}
        {!commentsLoading && comments.length === 0 && (
          <div style={{ textAlign: 'center', padding: 'var(--space-8)', color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
            No comments yet. Be the first.
          </div>
        )}
        {comments.map(c => (
          <div key={c.id} style={{
            display: 'flex', gap: 'var(--space-3)',
            padding: 'var(--space-3) 0',
            borderBottom: '1px solid var(--color-border)',
          }}>
            <img src={c.commenter?.avatarUrl} alt={c.commenter?.username}
              style={{ width: 30, height: 30, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 4 }}>
                <span style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-primary)' }}>
                  @{c.commenter?.username}
                </span>
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
                  {timeAgo(c.createdAt)}
                </span>
              </div>
              <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5, wordBreak: 'break-word' }}>
                {c.content}
              </p>
              <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 6 }}>
                <button onClick={() => handleUpvote(c.id)} style={{
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)',
                  fontFamily: 'inherit', display: 'flex', alignItems: 'center', gap: 4,
                  padding: '2px 6px', borderRadius: 'var(--radius-sm)',
                  transition: 'color var(--transition-fast)',
                }}
                  onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-success)')}
                  onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-muted)')}
                >
                  ▲ <span style={{ fontFamily: 'var(--font-mono)' }}>{c.upvotes ?? 0}</span>
                </button>
                {user?.id === c.userId && (
                  <button onClick={() => handleDeleteComment(c.id)} style={{
                    background: 'none', border: 'none', cursor: 'pointer',
                    fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)',
                    fontFamily: 'inherit', padding: '2px 6px', borderRadius: 'var(--radius-sm)',
                    transition: 'color var(--transition-fast)',
                  }}
                    onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-error)')}
                    onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-muted)')}
                  >Delete</button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
