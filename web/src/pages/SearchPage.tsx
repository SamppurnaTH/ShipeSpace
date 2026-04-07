import { useState, useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { searchShipsAndUsers } from '../api/endpoints'
import { Spinner } from '../components/Spinner'
import { Badge } from '../components/Badge'
import { ShipCard } from '../components/ShipCard'

interface SearchResult {
  users: any[]
  ships: any[]
}

const DEBOUNCE_MS = 350

export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get('q') || ''
  const [searchInput, setSearchInput] = useState(q)
  const [results, setResults] = useState<SearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'all' | 'users' | 'ships'>('all')
  // Holds the AbortController for the in-flight request so we can cancel it
  const abortRef = useRef<AbortController | null>(null)

  // Sync input when URL param changes (e.g. TopNav search navigates here)
  useEffect(() => { setSearchInput(q) }, [q])

  // Debounced search: fires 350ms after the user stops typing.
  // Cancels any in-flight request before issuing a new one.
  useEffect(() => {
    if (!searchInput.trim()) {
      setResults(null)
      setError(null)
      setLoading(false)
      return
    }

    const timer = setTimeout(() => {
      // Cancel previous in-flight request
      if (abortRef.current) abortRef.current.abort()
      const controller = new AbortController()
      abortRef.current = controller

      setLoading(true)
      setError(null)

      searchShipsAndUsers(searchInput.trim())
        .then(res => {
          if (!controller.signal.aborted) setResults(res.data)
        })
        .catch(err => {
          if (controller.signal.aborted) return // stale request — ignore
          const status = err?.response?.status
          if (status === 429) {
            setError('Too many searches. Wait a moment and try again.')
          } else {
            setError('Search is temporarily unavailable. Try again shortly.')
          }
          setResults(null)
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false)
        })
    }, DEBOUNCE_MS)

    return () => {
      clearTimeout(timer)
      // Don't abort here — let the in-flight request finish if the component
      // unmounts mid-flight; the aborted check above handles stale results.
    }
  }, [searchInput])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    // Update URL so the query is shareable/bookmarkable
    if (searchInput.trim()) setSearchParams({ q: searchInput.trim() })
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>
      {/* Search Header */}
      <div style={{ marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, margin: '0 0 var(--space-4) 0' }}>Search</h1>
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <input
            autoFocus
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search for builders, tech stacks, or ships..."
            style={{
              flex: 1, padding: '12px 16px', borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--color-border)', background: 'var(--color-surface)',
              fontSize: 'var(--text-base)', fontFamily: 'inherit',
              color: 'var(--color-text-primary)', outline: 'none',
              transition: 'border-color var(--transition-fast)',
            }}
            onFocus={e => e.currentTarget.style.borderColor = 'var(--color-brand)'}
            onBlur={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
          />
          <button type="submit" disabled={!searchInput.trim()} style={{
            padding: '0 24px', borderRadius: 'var(--radius-lg)',
            border: 'none', background: searchInput.trim() ? 'var(--color-brand)' : 'var(--color-surface-2)',
            color: searchInput.trim() ? '#fff' : 'var(--color-text-muted)',
            cursor: searchInput.trim() ? 'pointer' : 'default',
            fontSize: 'var(--text-base)', fontWeight: 600, fontFamily: 'inherit',
            transition: 'all var(--transition-fast)',
          }}>
            Search
          </button>
        </form>
      </div>

      {loading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
          <Spinner size={28} />
        </div>
      )}

      {!loading && !searchInput.trim() && (
        <div style={{
          textAlign: 'center', padding: 'var(--space-10)',
          color: 'var(--color-text-muted)',
        }}>
          <div style={{ fontSize: 48, marginBottom: 'var(--space-3)' }}>🔍</div>
          <div style={{ fontSize: 'var(--text-lg)', fontWeight: 500 }}>
            Type something to explore ShipSpace
          </div>
        </div>
      )}

      {!loading && error && (
        <div style={{
          textAlign: 'center', padding: 'var(--space-10)',
          background: 'var(--color-error-bg)',
          border: '1px solid var(--color-error-border)',
          borderRadius: 'var(--radius-lg)',
          color: 'var(--color-error)',
        }}>
          <div style={{ fontSize: 32, marginBottom: 'var(--space-3)' }}>⚠</div>
          <div style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>{error}</div>
        </div>
      )}

      {!loading && searchInput.trim() && results && !error && (
        <>
          {/* Tabs */}
          <div style={{
            display: 'flex', gap: 'var(--space-4)',
            borderBottom: '1px solid var(--color-border)',
            marginBottom: 'var(--space-5)',
          }}>
            {[
              { key: 'all', label: `All (${results.users.length + results.ships.length})` },
              { key: 'users', label: `Builders (${results.users.length})` },
              { key: 'ships', label: `Ships (${results.ships.length})` },
            ].map(tab => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key as any)}
                style={{
                  padding: '12px 0', border: 'none', background: 'none',
                  borderBottom: activeTab === tab.key ? '2px solid var(--color-brand)' : '2px solid transparent',
                  color: activeTab === tab.key ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
                  fontSize: 'var(--text-sm)', fontWeight: activeTab === tab.key ? 600 : 500,
                  cursor: 'pointer', transition: 'all var(--transition-fast)',
                  fontFamily: 'inherit',
                }}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Users */}
          {(activeTab === 'all' || activeTab === 'users') && results.users.length > 0 && (
            <div style={{ marginBottom: 'var(--space-6)' }}>
              {activeTab === 'all' && (
                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Builders
                </div>
              )}
              <div style={{ display: 'grid', gap: 'var(--space-3)' }}>
                {results.users.map(u => (
                  <Link key={u.id} to={`/profile/${u.username}`} style={{
                    display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
                    padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)',
                    background: 'var(--color-surface)', border: '1px solid var(--color-border)',
                    textDecoration: 'none', transition: 'all var(--transition-fast)',
                  }}
                    onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--color-brand)'}
                    onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
                  >
                    {u.avatarUrl ? (
                      <img src={u.avatarUrl} alt={u.username} style={{ width: 48, height: 48, borderRadius: '50%', objectFit: 'cover' }} />
                    ) : (
                      <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'var(--color-brand-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-brand)', fontWeight: 700 }}>?</div>
                    )}
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                        <span style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--color-text-primary)' }}>@{u.username}</span>
                        {u.rank && <Badge variant="brand">{u.rank}</Badge>}
                      </div>
                      {u.bio && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginTop: 2, display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{u.bio}</div>}
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          )}

          {/* Ships */}
          {(activeTab === 'all' || activeTab === 'ships') && results.ships.length > 0 && (
            <div>
              {activeTab === 'all' && (
                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Ships
                </div>
              )}
              <div>
                {results.ships.map(s => (
                  <ShipCard
                    key={s.id}
                    ship={s}
                    user={s.user}
                    project={s.project}
                    liked={false}
                    canLike={false}
                    onLike={() => {}}
                  />
                ))}
              </div>
            </div>
          )}

          {results.users.length === 0 && results.ships.length === 0 && (
            <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
              <div style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>No results found for "{searchInput}"</div>
              <div style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--space-2)' }}>Try a different search term.</div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
