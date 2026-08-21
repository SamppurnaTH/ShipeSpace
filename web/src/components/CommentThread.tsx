import { useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteComment, removeCommentUpvote, upvoteComment } from '../api/endpoints'
import { toastSuccess, toastError } from '../components/Toast'
import { Icon } from './Icon'

interface Comment {
  id: string
  content: string
  createdAt: string
  upvotes?: number
  userId: string
  upvotedByCurrentUser?: boolean
  commenter?: { id?: string; username: string; avatarUrl: string; rank?: string; level?: number }
}

interface CommentThreadProps {
  comments: Comment[]
  currentUserId?: string
  canInteract?: boolean
  onDelete: (commentId: string) => void
  onUpvoteChange: (commentId: string, upvoted: boolean, upvoteCount: number) => void
}

export function CommentThread({ comments, currentUserId, canInteract = false, onDelete, onUpvoteChange }: CommentThreadProps) {
  const [pendingUpvotes, setPendingUpvotes] = useState<Set<string>>(new Set())
  const [pendingDeletes, setPendingDeletes] = useState<Set<string>>(new Set())

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
    if (!canInteract || pendingUpvotes.has(comment.id)) return
    const wasUpvoted = Boolean(comment.upvotedByCurrentUser)
    setPendingUpvotes(prev => {
      const n = new Set(prev)
      n.add(comment.id)
      return n
    })
    try {
      const res = await (wasUpvoted ? removeCommentUpvote(comment.id) : upvoteComment(comment.id))
      onUpvoteChange(comment.id, res.data.upvoted, res.data.upvoteCount)
    } catch {
      toastError(wasUpvoted ? 'Failed to remove upvote' : 'Failed to upvote comment')
    } finally {
      setPendingUpvotes(prev => {
        const n = new Set(prev)
        n.delete(comment.id)
        return n
      })
    }
  }

  const handleDelete = async (commentId: string) => {
    if (pendingDeletes.has(commentId)) return
    setPendingDeletes(prev => {
      const n = new Set(prev)
      n.add(commentId)
      return n
    })
    try {
      await deleteComment(commentId)
      onDelete(commentId)
      toastSuccess('Comment deleted')
    } catch {
      toastError('Failed to delete comment')
    } finally {
      setPendingDeletes(prev => {
        const n = new Set(prev)
        n.delete(commentId)
        return n
      })
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
      {comments.map(comment => {
        const isOwner = currentUserId && comment.userId === currentUserId
        const upvoted = Boolean(comment.upvotedByCurrentUser)
        const pendingUpvote = pendingUpvotes.has(comment.id)
        const pendingDelete = pendingDeletes.has(comment.id)
        const commenter = comment.commenter

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
            {commenter?.avatarUrl ? (
              <Link to={`/profile/${commenter.id ?? commenter.username}`}>
                <img src={commenter.avatarUrl} alt={commenter.username}
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
                <Link to={`/profile/${commenter?.id ?? commenter?.username}`}
                  style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', textDecoration: 'none' }}>
                  @{commenter?.username ?? 'unknown'}
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
                  disabled={!canInteract || pendingUpvote}
                  aria-pressed={upvoted}
                  aria-label={upvoted ? 'Remove comment upvote' : 'Upvote comment'}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 4,
                    background: upvoted ? 'var(--color-brand-light)' : 'none',
                    border: 'none', borderRadius: 'var(--radius-sm)',
                    padding: '2px 8px', cursor: canInteract && !pendingUpvote ? 'pointer' : 'default',
                    color: upvoted ? 'var(--color-brand)' : 'var(--color-text-muted)',
                    fontSize: 'var(--text-xs)', fontFamily: 'inherit',
                    fontWeight: upvoted ? 600 : 400,
                    opacity: !canInteract || pendingUpvote ? 0.65 : 1,
                    transition: 'all var(--transition-fast)',
                  }}
                >
                  <Icon name="thumb" size={13} /> {comment.upvotes ?? 0}
                </button>

                {isOwner && (
                  <button
                    onClick={() => handleDelete(comment.id)}
                    disabled={pendingDelete}
                    aria-label="Delete comment"
                    style={{
                      background: 'none', border: 'none', cursor: pendingDelete ? 'default' : 'pointer',
                      color: 'var(--color-text-muted)', fontSize: 'var(--text-xs)',
                      fontFamily: 'inherit', padding: '2px 6px',
                      borderRadius: 'var(--radius-sm)',
                      opacity: pendingDelete ? 0.65 : 1,
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
