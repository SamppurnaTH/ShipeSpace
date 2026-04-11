import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Spinner } from './Spinner'
import { getOAuthState } from '../api/endpoints'
import { useUnreadCount } from '../hooks/useUnreadCount'

// LinkedIn-style SVG icons
const HomeIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
    <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>
  </svg>
)
const TrendingIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
    <path d="M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z"/>
  </svg>
)
const ProjectsIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
    <path d="M20 6h-2.18c.07-.44.18-.88.18-1.36C18 2.53 15.47 0 12.36 0c-1.73 0-3.24.87-4.19 2.19L12 6H8L6.91 3.9C6.09 2.76 4.8 2 3.36 2 1.51 2 0 3.51 0 5.36c0 .48.11.92.18 1.36H0v2h20v-2zM4 20c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10H4v10z"/>
  </svg>
)
const CollabIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
    <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/>
  </svg>
)
const BellIcon = ({ count }: { count: number }) => (
  <div style={{ position: 'relative', display: 'flex' }}>
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/>
    </svg>
    {count > 0 && (
      <span style={{
        position: 'absolute', top: -4, right: -4,
        minWidth: 15, height: 15,
        borderRadius: 'var(--radius-full)',
        background: 'var(--color-error)',
        color: '#fff', fontSize: 9, fontWeight: 700,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '0 3px',
      }}>
        {count > 9 ? '9+' : count}
      </span>
    )}
  </div>
)

const NAV_TABS = [
  { path: '/feed',           label: 'Home',          Icon: HomeIcon },
  { path: '/trending',       label: 'Trending',      Icon: TrendingIcon },
  { path: '/projects',       label: 'Projects',      Icon: ProjectsIcon },
  { path: '/collaborations', label: 'Network',       Icon: CollabIcon },
]

export function TopNav() {
  const { user, logout, loading } = useAuth()
  const unread = useUnreadCount()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [searchInput, setSearchInput] = useState('')
  const navigate = useNavigate()
  const location = useLocation()

  const handleLogin = async () => {
    try {
      const res = await getOAuthState()
      const state = res.data.state
      const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID
      const redirectUri = import.meta.env.VITE_GITHUB_REDIRECT_URI || `${window.location.origin}/auth/callback`
      window.location.href = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${state}&scope=read:user,user:email`
    } catch {
      alert('Could not reach the server.')
    }
  }

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = searchInput.trim()
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`)
  }

  return (
    <header style={{
      height: 'var(--header-height)',
      background: 'var(--color-surface)',
      borderBottom: '1px solid var(--color-border)',
      position: 'sticky', top: 0, zIndex: 200,
      boxShadow: 'var(--shadow-sm)',
    }}>
      <div style={{
        maxWidth: 'var(--layout-max)',
        margin: '0 auto',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        padding: '0 var(--space-4)',
        gap: 'var(--space-3)',
      }}>

        {/* Logo */}
        <Link to={user ? '/feed' : '/'} style={{
          display: 'flex', alignItems: 'center', gap: 6,
          textDecoration: 'none', flexShrink: 0,
        }}>
          <div style={{
            width: 34, height: 34, borderRadius: 'var(--radius-md)',
            background: 'var(--color-brand)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 18,
          }}>🚀</div>
        </Link>

        {/* Search */}
        <form onSubmit={handleSearch} style={{ flexShrink: 0 }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: 'var(--color-surface-2)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)',
            padding: '0 12px',
            height: 34,
            width: 220,
            transition: 'border-color var(--transition-fast)',
          }}
            onFocusCapture={e => (e.currentTarget.style.borderColor = 'var(--color-brand)')}
            onBlurCapture={e => (e.currentTarget.style.borderColor = 'var(--color-border)')}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-muted)" strokeWidth="2.5">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
            <input
              type="text"
              value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
              placeholder="Search"
              style={{
                background: 'none', border: 'none', outline: 'none',
                fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)',
                width: '100%',
              }}
            />
          </div>
        </form>

        {/* Center nav tabs — LinkedIn style */}
        {user && (
          <nav style={{
            display: 'flex', alignItems: 'stretch',
            flex: 1, justifyContent: 'center',
            height: '100%',
            gap: 4,
          }}>
            {NAV_TABS.map(({ path, label, Icon }) => {
              const active = location.pathname === path ||
                (path === '/feed' && location.pathname.startsWith('/feed'))
              return (
                <Link
                  key={path}
                  to={path}
                  style={{
                    display: 'flex', flexDirection: 'column',
                    alignItems: 'center', justifyContent: 'center',
                    gap: 2,
                    padding: '0 20px',
                    textDecoration: 'none',
                    color: active ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
                    borderBottom: active ? '2px solid var(--color-text-primary)' : '2px solid transparent',
                    fontSize: 'var(--text-xs)',
                    fontWeight: active ? 600 : 400,
                    transition: 'color var(--transition-fast)',
                    minWidth: 72,
                    position: 'relative',
                  }}
                  onMouseEnter={e => {
                    if (!active) e.currentTarget.style.color = 'var(--color-text-secondary)'
                  }}
                  onMouseLeave={e => {
                    if (!active) e.currentTarget.style.color = 'var(--color-text-muted)'
                  }}
                >
                  <Icon />
                  {label}
                </Link>
              )
            })}

            {/* Notifications tab */}
            <Link
              to="/notifications"
              style={{
                display: 'flex', flexDirection: 'column',
                alignItems: 'center', justifyContent: 'center',
                gap: 2,
                padding: '0 20px',
                textDecoration: 'none',
                color: location.pathname === '/notifications' ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
                borderBottom: location.pathname === '/notifications' ? '2px solid var(--color-text-primary)' : '2px solid transparent',
                fontSize: 'var(--text-xs)',
                fontWeight: location.pathname === '/notifications' ? 600 : 400,
                transition: 'color var(--transition-fast)',
                minWidth: 72,
              }}
            >
              <BellIcon count={unread} />
              Notifications
            </Link>
          </nav>
        )}

        {/* Right: auth */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginLeft: 'auto', flexShrink: 0 }}>
          {loading ? (
            <Spinner size={16} />
          ) : user ? (
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => setDropdownOpen(!dropdownOpen)}
                style={{
                  display: 'flex', flexDirection: 'column', alignItems: 'center',
                  gap: 2, background: 'none', border: 'none', cursor: 'pointer',
                  padding: '0 12px', height: '100%',
                  color: dropdownOpen ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
                  fontSize: 'var(--text-xs)',
                  borderBottom: dropdownOpen ? '2px solid var(--color-text-primary)' : '2px solid transparent',
                  minWidth: 60,
                }}
              >
                <img
                  src={user.avatarUrl}
                  alt={user.username}
                  style={{ width: 22, height: 22, borderRadius: '50%', objectFit: 'cover' }}
                />
                <span style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  Me
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M7 10l5 5 5-5z"/>
                  </svg>
                </span>
              </button>

              {dropdownOpen && (
                <>
                  <div style={{ position: 'fixed', inset: 0, zIndex: 99 }} onClick={() => setDropdownOpen(false)} />
                  <div style={{
                    position: 'absolute', right: 0, top: 'calc(100% + 4px)',
                    width: 220,
                    background: 'var(--color-surface)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-lg)',
                    boxShadow: 'var(--shadow-lg)',
                    zIndex: 100,
                    overflow: 'hidden',
                    animation: 'fadeIn 100ms ease',
                  }}>
                    {/* Profile preview */}
                    <div style={{ padding: 'var(--space-4)', borderBottom: '1px solid var(--color-border)' }}>
                      <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', marginBottom: 'var(--space-3)' }}>
                        <img src={user.avatarUrl} alt={user.username}
                          style={{ width: 48, height: 48, borderRadius: '50%', objectFit: 'cover' }} />
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                            {user.username}
                          </div>
                          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                            {user.rank} · Level {user.level}
                          </div>
                        </div>
                      </div>
                      <Link
                        to={`/profile/${user.username}`}
                        onClick={() => setDropdownOpen(false)}
                        style={{
                          display: 'block', textAlign: 'center',
                          padding: '6px', borderRadius: 'var(--radius-md)',
                          border: '1px solid var(--color-brand)',
                          color: 'var(--color-brand)',
                          fontSize: 'var(--text-sm)', fontWeight: 600,
                          textDecoration: 'none',
                          transition: 'background var(--transition-fast)',
                        }}
                        onMouseEnter={e => e.currentTarget.style.background = 'var(--color-brand-light)'}
                        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                      >
                        View Profile
                      </Link>
                    </div>

                    {[
                      { to: '/projects', label: 'My Projects' },
                      { to: '/settings', label: 'Settings' },
                    ].map(item => (
                      <Link
                        key={item.to}
                        to={item.to}
                        onClick={() => setDropdownOpen(false)}
                        style={menuItemStyle}
                        onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                      >
                        {item.label}
                      </Link>
                    ))}

                    <div style={{ borderTop: '1px solid var(--color-border)' }} />
                    <button
                      onClick={async () => { setDropdownOpen(false); await logout() }}
                      style={{ ...menuItemStyle, width: '100%', border: 'none', background: 'none', cursor: 'pointer', fontFamily: 'inherit', color: 'var(--color-error)', textAlign: 'left' }}
                      onMouseEnter={e => e.currentTarget.style.background = 'var(--color-error-bg)'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    >
                      Sign Out
                    </button>
                  </div>
                </>
              )}
            </div>
          ) : (
            <button onClick={handleLogin} style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '8px 20px', borderRadius: 'var(--radius-full)',
              background: 'transparent',
              border: '1.5px solid var(--color-brand)',
              color: 'var(--color-brand)',
              cursor: 'pointer', fontSize: 'var(--text-sm)', fontWeight: 600,
              transition: 'all var(--transition-fast)',
            }}
              onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-brand-light)' }}
              onMouseLeave={e => { e.currentTarget.style.background = 'transparent' }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/>
              </svg>
              Sign in
            </button>
          )}
        </div>
      </div>
    </header>
  )
}

const menuItemStyle: React.CSSProperties = {
  display: 'block',
  padding: '10px 16px',
  fontSize: 'var(--text-sm)',
  color: 'var(--color-text-primary)',
  textDecoration: 'none',
  transition: 'background var(--transition-fast)',
}
