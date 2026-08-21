import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { likeShip, searchShipsAndUsers, unlikeShip, type SearchResponse, type SearchShipResult } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Badge } from '../components/Badge'
import { Icon } from '../components/Icon'
import { ShipCard } from '../components/ShipCard'
import { Spinner } from '../components/Spinner'
import type { ShipImage } from '../utils/shipImages'

const DEBOUNCE_MS = 350
type SearchTab = 'all' | 'users' | 'ships'
const SEARCH_TABS: { key: SearchTab; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'users', label: 'Builders' },
  { key: 'ships', label: 'Ships' },
]

function normalizeShip(ship: SearchShipResult) {
  return {
    id: ship.id,
    content: ship.content ?? '',
    likeCount: ship.likeCount ?? 0,
    commentCount: ship.commentCount ?? 0,
    createdAt: ship.createdAt ?? new Date().toISOString(),
    images: (ship.images ?? []) as ShipImage[],
    externalLinks: (ship.externalLinks ?? []).map(link => ({ url: link.url, type: link.type ?? 'link' })),
  }
}

export function SearchPage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get('q') || ''
  const [searchInput, setSearchInput] = useState(q)
  const [results, setResults] = useState<SearchResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<SearchTab>('all')
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    setSearchInput(q)
  }, [q])

  const runSearch = useCallback((query: string) => {
    if (abortRef.current) abortRef.current.abort()
    const controller = new AbortController()
    abortRef.current = controller

    setLoading(true)
    setError(null)

    searchShipsAndUsers(query, 10, controller.signal)
      .then(res => {
        if (!controller.signal.aborted) setResults(res.data)
      })
      .catch(err => {
        if (controller.signal.aborted) return
        const status = err?.response?.status
        setError(status === 429
          ? 'Too many searches. Wait a moment and try again.'
          : 'Search is temporarily unavailable. Try again shortly.')
        setResults(null)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
  }, [])

  useEffect(() => {
    const query = searchInput.trim()
    if (!query) {
      if (abortRef.current) abortRef.current.abort()
      setResults(null)
      setError(null)
      setLoading(false)
      return
    }

    const timer = setTimeout(() => runSearch(query), DEBOUNCE_MS)

    return () => {
      clearTimeout(timer)
      if (abortRef.current) abortRef.current.abort()
    }
  }, [searchInput, runSearch])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const query = searchInput.trim()
    if (query) setSearchParams({ q: query })
  }

  const toggleLike = async (shipId: string) => {
    if (!user || user.banned) return
    const liked = likedIds.has(shipId)
    setLikedIds(prev => {
      const next = new Set(prev)
      if (liked) next.delete(shipId)
      else next.add(shipId)
      return next
    })
    setResults(prev => prev ? {
      ...prev,
      ships: prev.ships.map(ship => ship.id === shipId
        ? { ...ship, likeCount: Math.max(0, (ship.likeCount ?? 0) + (liked ? -1 : 1)) }
        : ship),
    } : prev)
    try {
      await (liked ? unlikeShip(shipId) : likeShip(shipId))
    } catch {
      setLikedIds(prev => {
        const next = new Set(prev)
        if (liked) next.add(shipId)
        else next.delete(shipId)
        return next
      })
      setResults(prev => prev ? {
        ...prev,
        ships: prev.ships.map(ship => ship.id === shipId
          ? { ...ship, likeCount: Math.max(0, (ship.likeCount ?? 0) + (liked ? 1 : -1)) }
          : ship),
      } : prev)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>
      <div style={{ marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, margin: '0 0 var(--space-4) 0' }}>Search</h1>
        <form onSubmit={handleSearch} role="search" style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <label htmlFor="shipspace-search" style={visuallyHiddenStyle}>
            Search builders, tech stacks, or ships
          </label>
          <input
            id="shipspace-search"
            autoFocus
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search for builders, tech stacks, or ships..."
            style={searchInputStyle}
          />
          <button type="submit" disabled={!searchInput.trim()} style={searchButtonStyle(Boolean(searchInput.trim()))}>
            Search
          </button>
        </form>
      </div>

      {loading && (
        <div aria-busy="true" style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
          <Spinner size={28} />
        </div>
      )}

      {!loading && !searchInput.trim() && (
        <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="search" size={48} /></div>
          <div style={{ fontSize: 'var(--text-lg)', fontWeight: 500 }}>
            Type something to explore ShipSpace
          </div>
        </div>
      )}

      {!loading && error && (
        <div role="alert" style={errorStyle}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="alert" size={32} /></div>
          <div style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>{error}</div>
          {searchInput.trim() && (
            <button type="button" onClick={() => runSearch(searchInput.trim())} style={retryStyle}>
              Retry search
            </button>
          )}
        </div>
      )}

      {!loading && searchInput.trim() && results && !error && (
        <>
          <div role="tablist" aria-label="Search result types" style={tabListStyle}>
            {SEARCH_TABS.map(tab => {
              const count = tab.key === 'all'
                ? results.users.length + results.ships.length
                : tab.key === 'users' ? results.users.length : results.ships.length
              return (
                <button
                  key={tab.key}
                  type="button"
                  role="tab"
                  aria-selected={activeTab === tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  style={tabStyle(activeTab === tab.key)}
                >
                  {tab.label} ({count})
                </button>
              )
            })}
          </div>

          {(activeTab === 'all' || activeTab === 'users') && results.users.length > 0 && (
            <div style={{ marginBottom: 'var(--space-6)' }}>
              {activeTab === 'all' && <SectionLabel>Builders</SectionLabel>}
              <div style={{ display: 'grid', gap: 'var(--space-3)' }}>
                {results.users.map(resultUser => (
                  <Link key={resultUser.id} to={`/profile/${resultUser.id}`} style={userResultStyle}>
                    {resultUser.avatarUrl ? (
                      <img src={resultUser.avatarUrl} alt={`${resultUser.username} avatar`} style={avatarStyle} />
                    ) : (
                      <div style={avatarFallbackStyle}>?</div>
                    )}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
                        <span style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--color-text-primary)', overflowWrap: 'anywhere' }}>@{resultUser.username}</span>
                        {resultUser.rank && <Badge variant="brand">{resultUser.rank}</Badge>}
                      </div>
                      {resultUser.bio && (
                        <div style={bioStyle}>{resultUser.bio}</div>
                      )}
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          )}

          {(activeTab === 'all' || activeTab === 'ships') && results.ships.length > 0 && (
            <div>
              {activeTab === 'all' && <SectionLabel>Ships</SectionLabel>}
              <div>
                {results.ships.map(ship => (
                  <ShipCard
                    key={ship.id}
                    ship={normalizeShip(ship)}
                    user={{
                      id: ship.user?.id ?? '',
                      username: ship.user?.username ?? 'unknown-builder',
                      avatarUrl: ship.user?.avatarUrl ?? '',
                      rank: ship.user?.rank,
                      level: ship.user?.level,
                    }}
                    project={{
                      id: ship.project?.id ?? '',
                      name: ship.project?.name ?? 'Untitled project',
                      techTags: ship.project?.techTags ?? [],
                    }}
                    liked={likedIds.has(ship.id)}
                    canLike={!!user && !user.banned}
                    onLike={() => toggleLike(ship.id)}
                  />
                ))}
              </div>
            </div>
          )}

          {results.users.length === 0 && results.ships.length === 0 && (
            <div role="status" style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
              <div style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>No results found for "{searchInput.trim()}"</div>
              <div style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-2)' }}>Try a different search term.</div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
      {children}
    </div>
  )
}

const visuallyHiddenStyle: React.CSSProperties = {
  position: 'absolute',
  width: 1,
  height: 1,
  padding: 0,
  margin: -1,
  overflow: 'hidden',
  clip: 'rect(0, 0, 0, 0)',
  whiteSpace: 'nowrap',
  border: 0,
}

const searchInputStyle: React.CSSProperties = {
  flex: 1,
  minWidth: 0,
  padding: '12px 16px',
  borderRadius: 'var(--radius-lg)',
  border: '1px solid var(--color-border)',
  background: 'var(--color-surface)',
  fontSize: 'var(--text-base)',
  fontFamily: 'inherit',
  color: 'var(--color-text-primary)',
}

const searchButtonStyle = (enabled: boolean): React.CSSProperties => ({
  padding: '0 24px',
  borderRadius: 'var(--radius-lg)',
  border: 'none',
  background: enabled ? 'var(--color-brand)' : 'var(--color-surface-2)',
  color: enabled ? '#fff' : 'var(--color-text-muted)',
  cursor: enabled ? 'pointer' : 'default',
  fontSize: 'var(--text-base)',
  fontWeight: 600,
  fontFamily: 'inherit',
})

const errorStyle: React.CSSProperties = {
  textAlign: 'center',
  padding: 'var(--space-10)',
  background: 'var(--color-error-bg)',
  border: '1px solid var(--color-error-border)',
  borderRadius: 'var(--radius-lg)',
  color: 'var(--color-error)',
}

const retryStyle: React.CSSProperties = {
  marginTop: 'var(--space-4)',
  padding: '10px 18px',
  borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-error-border)',
  background: 'var(--color-surface)',
  color: 'var(--color-error)',
  fontWeight: 700,
  cursor: 'pointer',
}

const tabListStyle: React.CSSProperties = {
  display: 'flex',
  gap: 'var(--space-4)',
  borderBottom: '1px solid var(--color-border)',
  marginBottom: 'var(--space-5)',
  overflowX: 'auto',
}

const tabStyle = (active: boolean): React.CSSProperties => ({
  padding: '12px 0',
  border: 'none',
  background: 'none',
  borderBottom: active ? '2px solid var(--color-brand)' : '2px solid transparent',
  color: active ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
  fontSize: 'var(--text-sm)',
  fontWeight: active ? 600 : 500,
  cursor: 'pointer',
  fontFamily: 'inherit',
  whiteSpace: 'nowrap',
})

const userResultStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 'var(--space-3)',
  padding: 'var(--space-4)',
  borderRadius: 'var(--radius-lg)',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  textDecoration: 'none',
}

const avatarStyle: React.CSSProperties = {
  width: 48,
  height: 48,
  borderRadius: '50%',
  objectFit: 'cover',
  flex: '0 0 auto',
}

const avatarFallbackStyle: React.CSSProperties = {
  ...avatarStyle,
  background: 'var(--color-brand-light)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: 'var(--color-brand)',
  fontWeight: 700,
}

const bioStyle: React.CSSProperties = {
  fontSize: 'var(--text-sm)',
  color: 'var(--color-text-secondary)',
  marginTop: 2,
  display: '-webkit-box',
  WebkitLineClamp: 1,
  WebkitBoxOrient: 'vertical',
  overflow: 'hidden',
}
