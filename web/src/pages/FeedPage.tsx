import { useEffect, useRef, useState, useCallback } from 'react'
import { getPersonalizedFeed, getTrendingFeed, likeShip, unlikeShip } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

interface Ship {
  id: string
  content: string
  likeCount: number
  commentCount: number
  trendingScore: number
  createdAt: string
}

interface FeedItem {
  ship: Ship
  user?: { username: string; avatarUrl: string }
  project?: { name: string; techTags: string[] }
}

interface Page {
  content: FeedItem[]
  totalPages: number
  number: number
}

export function FeedPage() {
  const { user } = useAuth()
  const [items, setItems] = useState<FeedItem[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [feedMode, setFeedMode] = useState<string>('—')
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())
  const loaderRef = useRef<HTMLDivElement>(null)

  const fetchPage = useCallback(async (pageNum: number) => {
    if (loading || !hasMore) return
    setLoading(true)
    try {
      const res = user
        ? await getPersonalizedFeed(pageNum, 20)
        : await getTrendingFeed(pageNum, 20)

      const mode = res.headers['x-feed-mode'] || (user ? 'personalized' : 'trending')
      setFeedMode(mode)

      const data: Page = res.data
      setItems(prev => pageNum === 0 ? data.content : [...prev, ...data.content])
      setHasMore(pageNum < data.totalPages - 1)
      setPage(pageNum)
    } catch (err) {
      console.error('Feed fetch failed', err)
      setHasMore(false) // stop the observer from retrying on connection errors
    } finally {
      setLoading(false)
    }
  }, [user, loading, hasMore])

  // Initial load
  useEffect(() => {
    setItems([])
    setPage(0)
    setHasMore(true)
    fetchPage(0)
  }, [user])

  // Infinite scroll via IntersectionObserver
  useEffect(() => {
    const el = loaderRef.current
    if (!el) return
    const obs = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && hasMore && !loading) {
        fetchPage(page + 1)
      }
    }, { threshold: 0.1 })
    obs.observe(el)
    return () => obs.disconnect()
  }, [page, hasMore, loading, fetchPage])

  const toggleLike = async (item: FeedItem) => {
    const id = item.ship.id
    const liked = likedIds.has(id)
    // Optimistic update
    setLikedIds(prev => {
      const next = new Set(prev)
      liked ? next.delete(id) : next.add(id)
      return next
    })
    setItems(prev => prev.map(i =>
      i.ship.id === id
        ? { ...i, ship: { ...i.ship, likeCount: i.ship.likeCount + (liked ? -1 : 1) } }
        : i
    ))
    try {
      liked ? await unlikeShip(id) : await likeShip(id)
    } catch {
      // Revert on failure
      setLikedIds(prev => {
        const next = new Set(prev)
        liked ? next.add(id) : next.delete(id)
        return next
      })
      setItems(prev => prev.map(i =>
        i.ship.id === id
          ? { ...i, ship: { ...i.ship, likeCount: i.ship.likeCount + (liked ? 1 : -1) } }
          : i
      ))
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.modeBar}>
        Feed mode: <strong>{feedMode}</strong>
        {feedMode === 'trending-fallback' && (
          <span style={styles.fallbackBadge}>⚠ degraded</span>
        )}
      </div>

      {items.map(item => (
        <div key={item.ship.id} style={styles.card}>
          <div style={styles.cardHeader}>
            {item.user?.avatarUrl && (
              <img src={item.user.avatarUrl} alt="" style={styles.avatar} />
            )}
            <div>
              <div style={styles.username}>{item.user?.username || 'unknown'}</div>
              {item.project && (
                <div style={styles.project}>
                  {item.project.name}
                  {item.project.techTags?.map(t => (
                    <span key={t} style={styles.tag}>{t}</span>
                  ))}
                </div>
              )}
            </div>
          </div>

          <p style={styles.content}>{item.ship.content}</p>

          <div style={styles.actions}>
            <button
              onClick={() => user && toggleLike(item)}
              style={{
                ...styles.likeBtn,
                color: likedIds.has(item.ship.id) ? '#f43f5e' : '#64748b',
              }}
              disabled={!user}
              title={user ? undefined : 'Login to like'}
            >
              {likedIds.has(item.ship.id) ? '♥' : '♡'} {item.ship.likeCount}
            </button>
            <span style={styles.meta}>
              💬 {item.ship.commentCount}
            </span>
            <span style={styles.meta}>
              {new Date(item.ship.createdAt).toLocaleDateString()}
            </span>
          </div>
        </div>
      ))}

      <div ref={loaderRef} style={styles.loader}>
        {loading && <span>Loading…</span>}
        {!hasMore && items.length > 0 && <span style={{ color: '#64748b' }}>End of feed</span>}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { maxWidth: 640, margin: '0 auto', padding: '16px 12px' },
  modeBar: {
    fontSize: 12, color: '#64748b', marginBottom: 16,
    padding: '6px 10px', background: '#f1f5f9', borderRadius: 6,
  },
  fallbackBadge: {
    marginLeft: 8, color: '#f59e0b', fontWeight: 'bold',
  },
  card: {
    background: '#fff', border: '1px solid #e2e8f0',
    borderRadius: 10, padding: 16, marginBottom: 12,
  },
  cardHeader: { display: 'flex', gap: 10, marginBottom: 10, alignItems: 'flex-start' },
  avatar: { width: 36, height: 36, borderRadius: '50%', objectFit: 'cover' },
  username: { fontWeight: 600, fontSize: 14 },
  project: { fontSize: 12, color: '#64748b', marginTop: 2 },
  tag: {
    display: 'inline-block', marginLeft: 4,
    background: '#e0f2fe', color: '#0369a1',
    borderRadius: 4, padding: '1px 5px', fontSize: 11,
  },
  content: { fontSize: 14, lineHeight: 1.6, margin: '0 0 12px', color: '#1e293b' },
  actions: { display: 'flex', gap: 16, alignItems: 'center' },
  likeBtn: {
    background: 'none', border: 'none', cursor: 'pointer',
    fontSize: 14, padding: 0, fontFamily: 'inherit',
  },
  meta: { fontSize: 13, color: '#94a3b8' },
  loader: { textAlign: 'center', padding: 24, fontSize: 13, color: '#94a3b8' },
}
