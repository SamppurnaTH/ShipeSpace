import { useState } from 'react'
import { Link } from 'react-router-dom'
import { RankBadge } from './RankBadge'
import { Icon } from './Icon'

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
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

// LinkedIn-style action icons
const LikeIcon = ({ filled }: { filled: boolean }) => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2">
    <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/>
    <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/>
  </svg>
)
const CommentIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
  </svg>
)
const ShareIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
    <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
    <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
  </svg>
)

export function ShipCard({ ship, user, project, liked, canLike, onLike, isOwner, onDelete }: ShipCardProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const truncated = ship.content.length > 280
  const displayContent = truncated && !expanded ? ship.content.slice(0, 280) + '…' : ship.content

  return (
    <article className="ship-feed-card">
      {/* Card body */}
      <div className="ship-feed-card-body">
        {/* Header */}
        <div style={{ display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-3)', alignItems: 'flex-start' }}>
          <Link to={user ? `/profile/${user.username}` : '#'} style={{ flexShrink: 0 }}>
            {user?.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.username}
                style={{ width: 44, height: 44, borderRadius: '50%', objectFit: 'cover' }} />
            ) : (
              <div style={{
                width: 44, height: 44, borderRadius: '50%',
                background: 'var(--color-surface-3)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 'var(--text-lg)', fontWeight: 700, color: 'var(--color-brand-dim)',
              }}>
                {(user?.username?.[0] ?? '?').toUpperCase()}
              </div>
            )}
          </Link>

          <div className="ship-feed-meta">
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
              <Link to={user ? `/profile/${user.username}` : '#'}
                style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', textDecoration: 'none' }}>
                {user?.username ?? 'unknown'}
              </Link>
              {user?.rank && user?.level !== undefined && (
                <RankBadge rank={user.rank} level={user.level} />
              )}
            </div>
            <div className="ship-feed-project-line">
              {project && (
                <Link to={`/projects/${project.id}`} style={{
                  fontSize: 'var(--text-xs)', color: 'var(--color-brand-dim)',
                  textDecoration: 'none',
                  fontWeight: 700,
                  transition: 'color var(--transition-fast)',
                }}
                  onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-brand-dim)')}
                  onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-muted)')}
                >
                  shipped to {project.name}
                </Link>
              )}
              {project && <span style={{ color: 'var(--color-border)', fontSize: 10 }}>·</span>}
              <time style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                {timeAgo(ship.createdAt)}
              </time>
            </div>
          </div>

          {/* Owner menu */}
          {isOwner && (
            <div style={{ position: 'relative', flexShrink: 0 }}>
              <button onClick={() => setMenuOpen(p => !p)} style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: 'var(--color-text-muted)', padding: '4px 8px',
                borderRadius: 'var(--radius-md)', fontSize: 18, lineHeight: 1,
                transition: 'background var(--transition-fast)',
              }}
                onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                onMouseLeave={e => e.currentTarget.style.background = 'none'}
              >...</button>
              {menuOpen && (
                <div style={{
                  position: 'absolute', right: 0, top: '100%', marginTop: 4,
                  background: 'var(--color-surface)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-md)',
                  zIndex: 10, minWidth: 130, overflow: 'hidden',
                }}>
                  {onDelete && (
                    <button onClick={() => { setMenuOpen(false); onDelete() }} style={{
                      display: 'block', width: '100%', padding: '10px 14px',
                      background: 'none', border: 'none', cursor: 'pointer',
                      color: 'var(--color-error)', fontSize: 'var(--text-sm)',
                      textAlign: 'left', fontFamily: 'inherit',
                      transition: 'background var(--transition-fast)',
                    }}
                      onMouseEnter={e => e.currentTarget.style.background = 'var(--color-error-bg)'}
                      onMouseLeave={e => e.currentTarget.style.background = 'none'}
                    >
                      Delete ship
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Content */}
        <p style={{
          fontSize: '15px', lineHeight: 1.62,
          color: 'var(--color-text-primary)',
          whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          marginBottom: 'var(--space-4)',
        }}>
          {displayContent}
          {truncated && (
            <button onClick={() => setExpanded(p => !p)} style={{
              background: 'none', border: 'none', cursor: 'pointer',
              color: 'var(--color-brand-dim)', fontSize: 'var(--text-sm)',
              fontFamily: 'inherit', padding: '0 4px',
            }}>
              {expanded ? 'see less' : 'see more'}
            </button>
          )}
        </p>

        {project && (
          <Link className="ship-project-chip" to={`/projects/${project.id}`}>
            <span>Project</span>
            <strong>{project.name}</strong>
          </Link>
        )}

        {/* Tech tags */}
        {project?.techTags && project.techTags.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 'var(--space-3)' }}>
            {project.techTags.slice(0, 5).map(tag => (
              <span key={tag} style={{
                fontSize: 'var(--text-xs)', padding: '2px 8px',
                background: 'var(--color-brand-light)',
                color: 'var(--color-brand-dim)',
                borderRadius: 'var(--radius-full)',
                fontFamily: 'var(--font-mono)',
                border: '1px solid rgba(10,102,194,0.2)',
              }}>{tag}</span>
            ))}
          </div>
        )}

        {/* Images */}
        {ship.images && ship.images.length > 0 && (
          <div style={{
            display: 'grid',
            gridTemplateColumns: ship.images.length === 1 ? '1fr' : '1fr 1fr',
            gap: 2, marginBottom: 'var(--space-3)',
            borderRadius: 'var(--radius-md)', overflow: 'hidden',
          }}>
            {ship.images.slice(0, 4).map((img, i) => (
              <img key={i} src={img} alt=""
                style={{ width: '100%', height: ship.images!.length === 1 ? 280 : 160, objectFit: 'cover' }} />
            ))}
          </div>
        )}

        {/* External links */}
        {ship.externalLinks && ship.externalLinks.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 'var(--space-3)' }}>
            {ship.externalLinks.map((l, i) => (
              <a key={i} href={l.url} target="_blank" rel="noopener noreferrer" style={{
                fontSize: 'var(--text-xs)', padding: '4px 10px',
                background: 'var(--color-surface-2)',
                border: '1px solid var(--color-border)',
                color: 'var(--color-text-secondary)',
                borderRadius: 'var(--radius-full)',
                display: 'flex', alignItems: 'center', gap: 4,
                textDecoration: 'none',
                transition: 'border-color var(--transition-fast)',
              }}
                onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--color-brand)'}
                onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
              >
                <Icon name="externalLink" size={13} /> {l.type || 'link'}
              </a>
            ))}
          </div>
        )}

        {/* Engagement counts */}
        {(ship.likeCount > 0 || ship.commentCount > 0) && (
          <div style={{
            display: 'flex', justifyContent: 'space-between',
            paddingBottom: 'var(--space-2)',
            fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)',
          }}>
            {ship.likeCount > 0 && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <Icon name="thumb" size={13} />
                {ship.likeCount}
              </span>
            )}
            {ship.commentCount > 0 && (
              <span style={{ marginLeft: 'auto' }}>
                {ship.commentCount} comment{ship.commentCount !== 1 ? 's' : ''}
              </span>
            )}
          </div>
        )}
      </div>

      <div className="ship-feed-actions">
        <ActionBtn
          onClick={canLike ? onLike : undefined}
          active={liked}
          disabled={!canLike}
          icon={<LikeIcon filled={liked} />}
          label={liked ? 'Appreciated' : 'Appreciate'}
          activeColor="var(--color-brand)"
        />
        <Link to={`/ships/${ship.id}`} style={{ flex: 1, textDecoration: 'none' }}>
          <ActionBtn icon={<CommentIcon />} label="Comment" />
        </Link>
        {project ? (
          <Link to={`/projects/${project.id}`} style={{ flex: 1, textDecoration: 'none' }}>
            <ActionBtn icon={<ShareIcon />} label="View Project" />
          </Link>
        ) : (
          <ActionBtn
            onClick={() => {
              if (navigator.share) {
                navigator.share({ url: `${window.location.origin}/ships/${ship.id}` }).catch(() => {})
              } else {
                navigator.clipboard.writeText(`${window.location.origin}/ships/${ship.id}`)
              }
            }}
            icon={<ShareIcon />}
            label="Share"
          />
        )}
      </div>
    </article>
  )
}

function ActionBtn({
  onClick, icon, label, active, disabled, activeColor,
}: {
  onClick?: () => void
  icon: React.ReactNode
  label: string
  active?: boolean
  disabled?: boolean
  activeColor?: string
}) {
  const [hover, setHover] = useState(false)
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
        padding: '10px 4px',
        background: hover ? 'var(--color-surface-2)' : 'none',
        border: 'none', borderRadius: 'var(--radius-md)',
        cursor: disabled ? 'default' : 'pointer',
        color: active ? (activeColor ?? 'var(--color-brand)') : hover ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
        fontSize: 'var(--text-xs)', fontWeight: active ? 600 : 500,
        fontFamily: 'inherit',
        transition: 'all var(--transition-fast)',
        opacity: disabled ? 0.4 : 1,
      }}
    >
      {icon}
      {label}
    </button>
  )
}

