import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getTrendingFeed } from '../api/endpoints'

/**
 * LinkedIn-style right sidebar.
 * Shows trending tech tags extracted from the feed.
 */
export function RightPanel() {
  const [tags, setTags] = useState<{ tag: string; count: number }[]>([])

  useEffect(() => {
    getTrendingFeed(0, 20)
      .then(res => {
        const items = res.data.content ?? []
        const freq: Record<string, number> = {}
        items.forEach((item: any) => {
          (item.project?.techTags ?? []).forEach((t: string) => {
            freq[t] = (freq[t] ?? 0) + 1
          })
        })
        const sorted = Object.entries(freq)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 8)
          .map(([tag, count]) => ({ tag, count }))
        setTags(sorted)
      })
      .catch(() => {})
  }, [])

  return (
    <aside style={{
      width: 'var(--col-right-width)',
      flexShrink: 0,
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-2)',
    }}>
      {/* Trending tags */}
      {tags.length > 0 && (
        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
          boxShadow: 'var(--shadow-sm)',
        }}>
          <div style={{
            fontSize: 'var(--text-sm)', fontWeight: 700,
            color: 'var(--color-text-primary)',
            marginBottom: 'var(--space-3)',
          }}>
            Trending on ShipSpace
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {tags.map(({ tag, count }, i) => (
              <Link
                key={tag}
                to={`/search?q=${encodeURIComponent(tag)}`}
                style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '6px var(--space-2)',
                  borderRadius: 'var(--radius-md)',
                  textDecoration: 'none',
                  transition: 'background var(--transition-fast)',
                }}
                onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
              >
                <div>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginBottom: 1 }}>
                    #{i + 1} · Tech
                  </div>
                  <div style={{
                    fontSize: 'var(--text-sm)', fontWeight: 600,
                    color: 'var(--color-text-primary)',
                    fontFamily: 'var(--font-mono)',
                  }}>
                    {tag}
                  </div>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                    {count} ship{count !== 1 ? 's' : ''}
                  </div>
                </div>
              </Link>
            ))}
          </div>

          <Link
            to="/trending"
            style={{
              display: 'block', marginTop: 'var(--space-3)',
              fontSize: 'var(--text-sm)', color: 'var(--color-brand)',
              fontWeight: 500, textDecoration: 'none',
              padding: '4px var(--space-2)',
              borderRadius: 'var(--radius-md)',
              transition: 'background var(--transition-fast)',
            }}
            onMouseEnter={e => e.currentTarget.style.background = 'var(--color-brand-light)'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            Show more →
          </Link>
        </div>
      )}

      {/* Footer links */}
      <div style={{ padding: 'var(--space-2) var(--space-2)', display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        {['About', 'Help', 'Privacy', 'Terms'].map(l => (
          <span key={l} style={{ fontSize: 10, color: 'var(--color-text-muted)', cursor: 'default' }}>{l}</span>
        ))}
        <span style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>· ShipSpace © 2025</span>
      </div>
    </aside>
  )
}
