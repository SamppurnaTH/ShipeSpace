import { useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteComment, upvoteComment } from '../api/endpoints'
import { toastSuccess, toastError } from '../components/Toast'
import { Icon } from './Icon'

interface Comment {
  id: string
  content: string
  createdAt: string
  upvotes: number
  userId: string
  user?: { username: string; avatarUrl: string; rank?: string; level?: number }
}

interface CommentThreadProps {
  comments: Comment[]
  currentUserId?: string
  onDelete: (commentId: string) => void
}

export function CommentThread({ comments, currentUserId, onDelete }: CommentThreadProps) {
  const [upvotedIds, setUpvotedIds] = useState<Set<string>>(new Set())

  const timeAgo = (iso: string) => {
    const diff = Date.now() - new Date(iso).getTime()
    const m = Math.floor(diff / 60000)
    if (m < 1) return 'just now'
    if (m < 60) return `${m}m ago`
    const h = Math.floor(m / 60)
    if (h < 24) return `${h}h ago`
    return `${Math.floor(h / 24)}d ago`
  }

  const handleUpvote = async (comment: Comment) => {
    const wasUpvoted = upvotedIds.has(comment.id)
    setUpvotedIds(prev => {
      const n = new Set(prev)
      if (wasUpvoted) n.delete(comment.id)
      else n.add(comment.id)
      return n
    })
    try {
      await upvoteComment(comment.id)
    } catch {
      setUpvotedIds(prev => {
        const n = new Set(prev)
        if (wasUpvoted) n.add(comment.id)
        else n.delete(comment.id)
        return n
      })
    }
  }

  const handleDelete = async (commentId: string) => {
    try {
      await deleteComment(commentId)
      onDelete(commentId)
      toastSuccess('Comment deleted')
    } catch {
      toastError('Failed to delete comment')
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
      {comments.map(comment => {
        const isOwner = currentUserId && comment.userId === currentUserId
        const upvoted = upvotedIds.has(comment.id)

        return (
          <div
            key={comment.id}
            style={{
              display: 'flex', gap: 'var(--space-3)',
              padding: 'var(--space-3)',
              borderRadius: 'var(--radius-md)',
              transition: 'background var(--transition-fast)',
            }}
            onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            {comment.user?.avatarUrl ? (
              <Link to={`/profile/${comment.user.username}`}>
                <img src={comment.user.avatarUrl} alt={comment.user.username}
                  style={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
              </Link>
            ) : (
              <div style={{
                width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
                background: 'var(--color-brand-light)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 'var(--text-xs)', fontWeight: 700, color: 'var(--color-brand)',
              }}>?</div>
            )}

            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 2 }}>
                <Link to={`/profile/${comment.user?.username}`}
                  style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', textDecoration: 'none' }}>
                  @{comment.user?.username ?? 'unknown'}
                </Link>
                <time style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  posted {timeAgo(comment.createdAt)}
                </time>
              </div>

              <p style={{
                fontSize: 'var(--text-sm)', lineHeight: 1.5,
                color: 'var(--color-text-primary)',
                margin: 0, wordBreak: 'break-word',
              }}>
                {comment.content}
              </p>

              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginTop: 'var(--space-2)' }}>
                <button
                  onClick={() => handleUpvote(comment)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 4,
                    background: upvoted ? 'var(--color-brand-light)' : 'none',
                    border: 'none', borderRadius: 'var(--radius-sm)',
                    padding: '2px 8px', cursor: 'pointer',
                    color: upvoted ? 'var(--color-brand)' : 'var(--color-text-muted)',
                    fontSize: 'var(--text-xs)', fontFamily: 'inherit',
                    fontWeight: upvoted ? 600 : 400,
                    transition: 'all var(--transition-fast)',
                  }}
                >
                  <Icon name="thumb" size={13} /> {(comment.upvotes ?? 0) + (upvoted ? 1 : 0)}
                </button>

                {isOwner && (
                  <button
                    onClick={() => handleDelete(comment.id)}
                    style={{
                      background: 'none', border: 'none', cursor: 'pointer',
                      color: 'var(--color-text-muted)', fontSize: 'var(--text-xs)',
                      fontFamily: 'inherit', padding: '2px 6px',
                      borderRadius: 'var(--radius-sm)',
                      transition: 'all var(--transition-fast)',
                    }}
                    onMouseEnter={e => { e.currentTarget.style.color = 'var(--color-error)' }}
                    onMouseLeave={e => { e.currentTarget.style.color = 'var(--color-text-muted)' }}
                  >
                    Delete
                  </button>
                )}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}
