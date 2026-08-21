import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { discoverBuilders, searchBuilders, type BuilderDiscoveryResult, type DiscoverySort } from '../api/endpoints'
import { Badge } from '../components/Badge'
import { Icon } from '../components/Icon'
import { Spinner } from '../components/Spinner'

const DEBOUNCE_MS = 350

const SORT_OPTIONS: { key: DiscoverySort; label: string }[] = [
  { key: 'discover', label: 'Discover' },
  { key: 'top', label: 'Top Builders' },
  { key: 'new', label: 'Newcomers' },
]

export function PeoplePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get('q') || ''
  const sortParam = (searchParams.get('sort') as DiscoverySort) || 'discover'
  
  const [searchInput, setSearchInput] = useState(q)
  const [results, setResults] = useState<BuilderDiscoveryResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    setSearchInput(q)
  }, [q])

  const runQuery = useCallback((query: string, sortMode: DiscoverySort) => {
    if (abortRef.current) abortRef.current.abort()
    const controller = new AbortController()
    abortRef.current = controller

    setLoading(true)
    setError(null)

    const request = query 
      ? searchBuilders(query, 0, 50, controller.signal)
      : discoverBuilders(0, 50, sortMode)

    request
      .then(res => {
        if (!controller.signal.aborted) setResults(res.data.content)
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setError('Unable to load builders. Try again shortly.')
        setResults([])
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
  }, [])

  useEffect(() => {
    const query = searchInput.trim()
    const timer = setTimeout(() => runQuery(query, sortParam), DEBOUNCE_MS)
    return () => {
      clearTimeout(timer)
      if (abortRef.current) abortRef.current.abort()
    }
  }, [searchInput, sortParam, runQuery])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const query = searchInput.trim()
    const newParams = new URLSearchParams()
    if (query) newParams.set('q', query)
    if (!query && sortParam !== 'discover') newParams.set('sort', sortParam)
    setSearchParams(newParams)
  }

  const setSortMode = (mode: DiscoverySort) => {
    const newParams = new URLSearchParams(searchParams)
    newParams.set('sort', mode)
    if (mode === 'discover') newParams.delete('sort')
    setSearchParams(newParams)
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>
      <div style={{ marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, margin: '0 0 var(--space-4) 0' }}>People</h1>
        
        <form onSubmit={handleSearch} role="search" style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <label htmlFor="people-search" style={visuallyHiddenStyle}>Search builders</label>
          <input
            id="people-search"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search builders by username or bio..."
            style={searchInputStyle}
          />
          <button type="submit" disabled={!searchInput.trim()} style={searchButtonStyle(Boolean(searchInput.trim()))}>
            Search
          </button>
        </form>
      </div>

      {!searchInput.trim() && (
        <div role="tablist" aria-label="Sort modes" style={tabListStyle}>
          {SORT_OPTIONS.map(tab => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={sortParam === tab.key}
              onClick={() => setSortMode(tab.key)}
              style={tabStyle(sortParam === tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}

      {loading && (
        <div aria-busy="true" style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
          <Spinner size={28} />
        </div>
      )}

      {!loading && error && (
        <div role="alert" style={errorStyle}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="alert" size={32} /></div>
          <div style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>{error}</div>
        </div>
      )}

      {!loading && !error && results.length > 0 && (
        <div style={{ display: 'grid', gap: 'var(--space-3)' }}>
          {results.map(user => (
            <Link key={user.id} to={/profile/ + user.id} style={userResultStyle}>
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.username + ' avatar'} style={avatarStyle} />
              ) : (
                <div style={avatarFallbackStyle}>?</div>
              )}
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--color-text-primary)' }}>@{user.username}</span>
                  {user.rank && <Badge variant="brand">{user.rank}</Badge>}
                </div>
                {user.bio && (
                  <div style={bioStyle}>{user.bio}</div>
                )}
                <div style={statsStyle}>
                  {user.followerCount !== undefined && <span>{user.followerCount} followers</span>}
                  {user.projectCount !== undefined && <span>{user.projectCount} projects</span>}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}

      {!loading && !error && results.length === 0 && (
        <div role="status" style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
          <div style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>
            {searchInput.trim() ? 'No builders found for "' + searchInput.trim() + '"' : "No builders found"}
          </div>
        </div>
      )}
    </div>
  )
}

const visuallyHiddenStyle: React.CSSProperties = {
  position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0, 0, 0, 0)', whiteSpace: 'nowrap', border: 0
}

const searchInputStyle: React.CSSProperties = {
  flex: 1, minWidth: 0, padding: '12px 16px', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)', background: 'var(--color-surface)', fontSize: 'var(--text-base)', fontFamily: 'inherit', color: 'var(--color-text-primary)'
}

const searchButtonStyle = (enabled: boolean): React.CSSProperties => ({
  padding: '0 24px', borderRadius: 'var(--radius-lg)', border: 'none', background: enabled ? 'var(--color-brand)' : 'var(--color-surface-2)', color: enabled ? '#fff' : 'var(--color-text-muted)', cursor: enabled ? 'pointer' : 'default', fontSize: 'var(--text-base)', fontWeight: 600, fontFamily: 'inherit'
})

const tabListStyle: React.CSSProperties = {
  display: 'flex', gap: 'var(--space-4)', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--space-5)', overflowX: 'auto'
}

const tabStyle = (active: boolean): React.CSSProperties => ({
  padding: '12px 0', border: 'none', background: 'none', borderBottom: active ? '2px solid var(--color-brand)' : '2px solid transparent', color: active ? 'var(--color-text-primary)' : 'var(--color-text-muted)', fontSize: 'var(--text-sm)', fontWeight: active ? 600 : 500, cursor: 'pointer', fontFamily: 'inherit', whiteSpace: 'nowrap'
})

const errorStyle: React.CSSProperties = {
  textAlign: 'center', padding: 'var(--space-10)', background: 'var(--color-error-bg)', border: '1px solid var(--color-error-border)', borderRadius: 'var(--radius-lg)', color: 'var(--color-error)'
}

const userResultStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', background: 'var(--color-surface)', border: '1px solid var(--color-border)', textDecoration: 'none'
}

const avatarStyle: React.CSSProperties = {
  width: 48, height: 48, borderRadius: '50%', objectFit: 'cover', flex: '0 0 auto'
}

const avatarFallbackStyle: React.CSSProperties = {
  ...avatarStyle, background: 'var(--color-brand-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-brand)', fontWeight: 700
}

const bioStyle: React.CSSProperties = {
  fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)', marginTop: 2, display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden'
}

const statsStyle: React.CSSProperties = {
  display: 'flex', gap: 'var(--space-3)', fontSize: '13px', color: 'var(--color-text-muted)', marginTop: 'var(--space-2)'
}
