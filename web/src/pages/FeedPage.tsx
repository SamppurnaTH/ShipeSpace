import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useFeed, type FeedType, type TrendingWindow } from '../hooks/useFeed'
import { ShipCard } from '../components/ShipCard'
import { SystemBanner } from '../components/SystemBanner'
import { FeedSkeleton, LoadingSpinner } from '../components/common/Loading'
import { deleteShip } from '../api/endpoints'
import { useToast } from '../components/Toast'
import { Icon } from '../components/Icon'

interface FeedPageProps { feedType?: FeedType; compactHome?: boolean }

const WINDOWS: { label: string; value: TrendingWindow }[] = [
  { label: 'All Time', value: 'all' },
  { label: '24h', value: '24h' },
  { label: '7d', value: '7d' },
  { label: '30d', value: '30d' },
]

export function FeedPage({ feedType = 'personalized', compactHome = false }: FeedPageProps) {
  const { user } = useAuth()
  const { toast } = useToast()
  const [trendWindow, setTrendWindow] = useState<TrendingWindow>('all')
  const { items, setItems, status, hasMore, feedMode, systemState, likedIds, loadMore, toggleLike } =
    useFeed(!!user, feedType, trendWindow)
  const loaderRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = loaderRef.current
    if (!el) return
    const obs = new IntersectionObserver(([e]) => { if (e.isIntersecting) loadMore() }, { threshold: 0.1 })
    obs.observe(el)
    return () => obs.disconnect()
  }, [loadMore])

  const handleDelete = async (shipId: string) => {
    if (!confirm('Delete this ship? This cannot be undone.')) return
    try {
      await deleteShip(shipId)
      setItems(prev => prev.filter(i => i.ship.id !== shipId))
      toast('Ship deleted', 'success')
    } catch {
      toast('Could not delete ship', 'error')
    }
  }

  const systemMsg = systemState !== 'normal' ? {
    degraded:      { variant: 'warning' as const, message: 'Personalized feed unavailable', detail: 'Showing trending content.' },
    'rate-limited':{ variant: 'warning' as const, message: 'Rate limited', detail: 'Slow down — requests will resume shortly.' },
    unavailable:   { variant: 'error'   as const, message: 'Service under load', detail: 'Some features may be slower.' },
  }[systemState] : null

  return (
    <div
      style={{ maxWidth: compactHome ? 'none' : 'var(--feed-max-width)', margin: '0 auto' }}
      aria-busy={status === 'loading'}
    >

      {/* Feed type tabs */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-4)', flexWrap: 'wrap' }}>
        {feedType === 'trending' && WINDOWS.map(w => (
          <button key={w.value} onClick={() => setTrendWindow(w.value)} style={{
            padding: '5px 14px', borderRadius: 'var(--radius-full)',
            border: 'none', cursor: 'pointer', fontFamily: 'inherit',
            fontSize: 'var(--text-sm)', fontWeight: 500,
            background: trendWindow === w.value ? 'var(--color-brand)' : 'var(--color-surface-2)',
            color: trendWindow === w.value ? '#fff' : 'var(--color-text-muted)',
            transition: 'all var(--transition-fast)',
          }}>{w.label}</button>
        ))}

        {feedMode && feedMode !== feedType && (
          <span style={{
            fontSize: 'var(--text-xs)', padding: '3px 10px',
            background: feedMode.includes('fallback') ? 'var(--color-warning-bg)' : 'var(--color-brand-light)',
            color: feedMode.includes('fallback') ? 'var(--color-warning)' : 'var(--color-brand-dim)',
            borderRadius: 'var(--radius-full)',
            fontFamily: 'var(--font-mono)',
          }}>
            {feedMode.includes('fallback') ? 'fallback mode' : feedMode}
          </span>
        )}
      </div>

      {systemMsg && <SystemBanner variant={systemMsg.variant} message={systemMsg.message} detail={systemMsg.detail} />}

      {/* Skeletons */}
      {status === 'loading' && items.length === 0 && <FeedSkeleton count={compactHome ? 3 : 5} />}

      {/* Empty */}
      {status === 'done' && items.length === 0 && (
        <div style={{ textAlign: 'center', padding: 'var(--space-12)', color: 'var(--color-text-muted)' }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-4)' }}><Icon name="ship" size={40} /></div>
          <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>
            {feedType === 'following' ? 'Your feed is empty' : "Nothing here yet — be the first to ship"}
          </div>
          <div style={{ fontSize: 'var(--text-sm)', marginBottom: feedType === 'personalized' ? 'var(--space-5)' : 0 }}>
            {feedType === 'following'
              ? 'Follow some builders to see their ships here.'
              : feedType === 'personalized'
              ? "Start by creating a project and shipping your first update."
              : 'Be the first to ship something.'}
          </div>
          {feedType === 'personalized' && (
            <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center', flexWrap: 'wrap' }}>
              <a href="/projects" style={{
                padding: '9px 22px', borderRadius: 'var(--radius-md)',
                background: 'var(--color-brand)', color: '#fff',
                fontSize: 'var(--text-sm)', fontWeight: 600, textDecoration: 'none',
              }}>
                Create a project
              </a>
              <a href="/trending" style={{
                padding: '9px 22px', borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border)', background: 'var(--color-surface)',
                color: 'var(--color-text-primary)',
                fontSize: 'var(--text-sm)', fontWeight: 600, textDecoration: 'none',
              }}>
                Browse trending
              </a>
            </div>
          )}
        </div>
      )}

      {/* Error */}
      {status === 'error' && items.length === 0 && (
        <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="alert" size={28} /></div>
          <div style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>
            Couldn't load the feed
          </div>
          <div style={{ fontSize: 'var(--text-sm)' }}>Check your connection or try again.</div>
        </div>
      )}

      {/* Items */}
      {items.map(item => (
        <ShipCard
          key={item.ship.id}
          ship={item.ship}
          user={item.user}
          project={item.project}
          liked={likedIds.has(item.ship.id)}
          canLike={!!user}
          onLike={() => toggleLike(item)}
          isOwner={!!user && user.id === item.user?.id}
          onDelete={() => handleDelete(item.ship.id)}
        />
      ))}

      {/* Infinite scroll sentinel */}
      <div ref={loaderRef} style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-6)' }}>
        {status === 'loading' && items.length > 0 && (
          <span className="inline-loading">
            <LoadingSpinner size={18} label="Loading more ships" />
            <span>Loading more ships...</span>
          </span>
        )}
        {!hasMore && items.length > 0 && (
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
            End of feed{' '}
            <button onClick={() => globalThis.window.scrollTo({ top: 0, behavior: 'smooth' })} style={{
              background: 'none', border: 'none', color: 'var(--color-brand-dim)',
              cursor: 'pointer', fontSize: 'inherit', fontFamily: 'inherit', padding: 0,
            }}>Back to top</button>
          </span>
        )}
      </div>
    </div>
  )
}
