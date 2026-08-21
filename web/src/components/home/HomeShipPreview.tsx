import { Link } from 'react-router-dom'
import type { FeedItem } from '../../hooks/useFeed'

function timeAgo(iso?: string) {
  if (!iso) return 'recently'
  const diff = Date.now() - new Date(iso).getTime()
  const minutes = Math.max(0, Math.floor(diff / 60000))
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

export function HomeShipPreview({ item }: { item: FeedItem }) {
  const ship = item.ship
  const user = item.user
  const project = item.project

  return (
    <article className="home-ship-preview">
      <div className="home-ship-preview-header">
        {user?.avatarUrl ? (
          <img src={user.avatarUrl} alt={`${user.username} avatar`} />
        ) : (
          <span aria-hidden="true">{(user?.username?.[0] ?? 'S').toUpperCase()}</span>
        )}
        <div>
          <Link to={user ? `/profile/${user.username}` : '/trending'}>{user?.username ?? 'Builder'}</Link>
          <p>
            {project?.name ? <Link to={`/projects/${project.id}`}>{project.name}</Link> : 'KREVO project'}
            <span> · </span>
            <time dateTime={ship.createdAt}>{timeAgo(ship.createdAt)}</time>
          </p>
        </div>
      </div>

      <p className="home-ship-preview-copy">{ship.content}</p>

      {project?.techTags && project.techTags.length > 0 && (
        <div className="home-tag-row" aria-label="Tech tags">
          {project.techTags.slice(0, 4).map(tag => <span key={tag}>{tag}</span>)}
        </div>
      )}

      <div className="home-ship-preview-meta">
        <span>{ship.likeCount} likes</span>
        <span>{ship.commentCount} comments</span>
      </div>
    </article>
  )
}
