import { useState } from 'react'
import { Link } from 'react-router-dom'
import { RankBadge } from './RankBadge'

interface Ship {
  id: string; content: string; likeCount: number; commentCount: number
  createdAt: string; editWindowOpen?: boolean; editWindowExpires?: string
  images?: string[]; externalLinks?: { url: string; type: string }[]
}
interface ShipCardProps {
  ship: Ship
  user?: { id: string; username: string; avatarUrl: string; rank?: string; level?: number }
  project?: { id: string; name: string; techTags: string[] }
  liked: boolean; canLike: boolean; onLike: () => void
  isOwner?: boolean; onDelete?: () => void
}

function timeAgo(iso: string) {
  const d = Date.now() - new Date(iso).getTime()
  const m = Math.floor(d / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h`
  return `${Math.floor(h / 24)}d`
}

export function ShipCard({ ship, user, project, liked, canLike, onLike, isOwner, onDelete }: ShipCardProps) {
  const [likeHover, setLikeHover] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  const truncated = ship.content.length > 320
  const [expanded, setExpanded] = useState(false)
  const displayContent = truncated && !expanded ? ship.content.slice(0, 320) + '…' : ship.content

  return (
    <article style={{
      background: 'var(--color-surface)',
      borderRadius: 'var(--radius-lg)',
      padding: 'var(--space-5)',
      marginBottom: 'var(--space-3)',
      transition: 'background var(--transition-fast)',
      position: 'relative',
      animation: 'fadeIn 200ms ease',
    }}
      onMouseEnter={e => (e.currentTarget.style.background = 'var(--color-surface-2)')}
      onMouseLeave={e => (e.currentTarget.style.background = 'var(--color-surface)')}
    >
      {/* Header */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-4)', alignItems: 'flex-start' }}>
        <Link to={user ? `/profile/${user.username}` : '#'} style={{ flexShrink: 0 }}>
          {user?.avatarUrl ? (
            <img src={user.avatarUrl} alt={user.username}
              style={{ width: 38, height: 38, borderRadius: '50%', objectFit: 'cover' }} />
          ) : (
            <div style={{
              width: 38, height: 38, borderRadius: '50%',
              background: 'var(--color-surface-3)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 'var(--text-sm)', fontWeight: 700, color: 'var(--color-brand-dim)',
            }}>
              {(user?.username?.[0] ?? '?').toUpperCase()}
            </div>
          )}
        </Link>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            <Link to={user ? `/profile/${user.username}` : '#'}
              style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
              @{user?.username ?? 'unknown'}
            </Link>
            {user?.rank && user?.level !== undefined && (
              <RankBadge rank={user.rank} level={user.level} />
            )}
          </div>
          {project && (
            <Link to={`/projects/${project.id}`} style={{
              fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)',
              marginTop: 2, display: 'block',
              transition: 'color var(--transition-fast)',
            }}
              onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-brand-dim)')}
              onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-muted)')}
            >
              {project.name}
            </Link>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexShrink: 0 }}>
          <time style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
            {timeAgo(ship.createdAt)}
          </time>
          {isOwner && (
            <div style={{ position: 'relative' }}>
              <button onClick={() => setMenuOpen(p => !p)} style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: 'var(--color-text-muted)', padding: '2px 6px',
                borderRadius: 'var(--radius-sm)', fontSize: 16,
                transition: 'color var(--transition-fast)',
              }}>···</button>
              {menuOpen && (
                <div style={{
                  position: 'absolute', right: 0, top: '100%', marginTop: 4,
                  background: 'var(--color-surface-4)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-md)',
                  zIndex: 10, minWidth: 120,
                  overflow: 'hidden',
                }}>
                  {onDelete && (
                    <button onClick={() => { setMenuOpen(false); onDelete() }} style={{
                      display: 'block', width: '100%', padding: '8px 14px',
                      background: 'none', border: 'none', cursor: 'pointer',
                      color: 'var(--color-error)', fontSize: 'var(--text-sm)',
                      textAlign: 'left', fontFamily: 'inherit',
                      transition: 'background var(--transition-fast)',
                    }}
                      onMouseEnter={e => (e.currentTarget.style.background = 'var(--color-error-bg)')}
                      onMouseLeave={e => (e.currentTarget.style.background = 'none')}
                    >
                      Delete
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Content */}
      <p style={{
        fontSize: 'var(--text-base)', lineHeight: 'var(--leading-normal)',
        color: 'var(--color-text-secondary)',
        marginBottom: 'var(--space-4)',
        whiteSpace: 'pre-wrap', wordBreak: 'break-word',
      }}>
        {displayContent}
        {truncated && (
          <button onClick={() => setExpanded(p => !p)} style={{
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--color-brand-dim)', fontSize: 'var(--text-sm)',
            fontFamily: 'inherit', padding: '0 4px',
          }}>
            {expanded ? ' show less' : ' show more'}
          </button>
        )}
      </p>

      {/* Tech tags */}
      {project?.techTags && project.techTags.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 'var(--space-3)' }}>
          {project.techTags.slice(0, 4).map(tag => (
            <span key={tag} style={{
              fontSize: 'var(--text-xs)', padding: '2px 8px',
              background: 'var(--color-brand-light)',
              color: 'var(--color-brand-dim)',
              borderRadius: 'var(--radius-sm)',
              fontFamily: 'var(--font-mono)',
            }}>{tag}</span>
          ))}
        </div>
      )}

      {/* External links */}
      {ship.externalLinks && ship.externalLinks.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 'var(--space-3)' }}>
          {ship.externalLinks.map((l, i) => (
            <a key={i} href={l.url} target="_blank" rel="noopener noreferrer" style={{
              fontSize: 'var(--text-xs)', padding: '3px 10px',
              background: 'var(--color-surface-3)',
              color: 'var(--color-text-secondary)',
              borderRadius: 'var(--radius-sm)',
              display: 'flex', alignItems: 'center', gap: 4,
              transition: 'color var(--transition-fast)',
            }}
              onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-brand-dim)')}
              onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-secondary)')}
            >
              🔗 {l.type || 'link'}
            </a>
          ))}
        </div>
      )}

      {/* Actions */}
      <div style={{
        display: 'flex', gap: 'var(--space-4)', alignItems: 'center',
        paddingTop: 'var(--space-3)',
        borderTop: '1px solid var(--color-border)',
      }}>
        <button
          onClick={canLike ? onLike : undefined}
          disabled={!canLike}
          onMouseEnter={() => setLikeHover(true)}
          onMouseLeave={() => setLikeHover(false)}
          aria-label={`${liked ? 'Unlike' : 'Like'} — ${ship.likeCount}`}
          aria-pressed={liked}
          style={{
            display: 'flex', alignItems: 'center', gap: 5,
            background: liked || likeHover ? 'var(--color-like-bg)' : 'none',
            border: 'none', borderRadius: 'var(--radius-sm)',
            padding: '4px 8px', cursor: canLike ? 'pointer' : 'default',
            color: liked || (likeHover && canLike) ? 'var(--color-like)' : 'var(--color-text-muted)',
            fontSize: 'var(--text-sm)', fontFamily: 'inherit',
            transition: 'all var(--transition-fast)',
            opacity: !canLike ? 0.5 : 1,
          }}
        >
          <span style={{ fontSize: 15 }}>{liked ? '♥' : '♡'}</span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>{ship.likeCount}</span>
        </button>

        <Link to={`/ships/${ship.id}`} style={{
          display: 'flex', alignItems: 'center', gap: 5,
          fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)',
          padding: '4px 8px', borderRadius: 'var(--radius-sm)',
          transition: 'color var(--transition-fast)',
        }}
          onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-brand-dim)')}
          onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-muted)')}
        >
          <span style={{ fontSize: 14 }}>💬</span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>{ship.commentCount}</span>
        </Link>
      </div>
    </article>
  )
}
