import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Spinner } from './Spinner'
import { getOAuthState } from '../api/endpoints'
import { useUnreadCount } from '../hooks/useUnreadCount'

export function TopNav() {
  const { user, logout, loading } = useAuth()
  const unread = useUnreadCount()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [searchInput, setSearchInput] = useState('')
  const navigate = useNavigate()

  const handleLogin = async () => {
    try {
      const res = await getOAuthState()
      const state = res.data.state
      const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID
      const redirectUri = import.meta.env.VITE_GITHUB_REDIRECT_URI
        || `${window.location.origin}/auth/callback`
      window.location.href =
        `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${state}&scope=read:user,user:email`
    } catch {
      alert('Could not reach the server.')
    }
  }

  const handleLogout = async () => {
    setDropdownOpen(false)
    await logout()
  }

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = searchInput.trim()
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`)
  }

  return (
    <header style={{
      height: 64, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '0 var(--space-6)',
      background: 'var(--color-surface)',
      borderBottom: '1px solid var(--color-border)',
      position: 'sticky', top: 0, zIndex: 100,
      boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
    }}>
      {/* Left: Logo */}
      <Link to={user ? '/feed' : '/'} style={{
        fontWeight: 700, fontSize: 'var(--text-lg)',
        color: 'var(--color-text-primary)', letterSpacing: '-0.3px',
        display: 'flex', alignItems: 'center', gap: 8,
        textDecoration: 'none',
      }}>
        <span style={{ fontSize: 22 }}>🚀</span>
        ShipSpace
      </Link>

      {/* Center: Search */}
      <form
        onSubmit={handleSearch}
        style={{ flex: '0 1 400px', display: 'flex', margin: '0 var(--space-6)' }}
      >
        <input
          type="text"
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          placeholder="Search ships, projects, builders..."
          style={{
            width: '100%',
            padding: '8px 14px',
            borderRadius: 'var(--radius-full)',
            border: '1px solid var(--color-border)',
            background: 'var(--color-surface-2)',
            fontSize: 'var(--text-sm)',
            color: 'var(--color-text-primary)',
            outline: 'none',
            transition: 'border-color var(--transition-fast)',
          }}
          onFocus={e => e.currentTarget.style.borderColor = 'var(--color-brand)'}
          onBlur={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
        />
      </form>

      {/* Right: Actions */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
        {loading ? (
          <Spinner size={18} />
        ) : user ? (
          <>
            {/* Notification bell */}
            <Link to="/notifications" style={{
              position: 'relative', display: 'flex', alignItems: 'center',
              color: 'var(--color-text-secondary)', textDecoration: 'none',
              padding: 6, borderRadius: 'var(--radius-sm)',
              transition: 'background var(--transition-fast)',
            }}
              onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
              onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              {unread > 0 && (
                <span style={{
                  position: 'absolute', top: 2, right: 2,
                  minWidth: 16, height: 16, borderRadius: 'var(--radius-full)',
                  background: 'var(--color-error)',
                  color: '#fff', fontSize: 10, fontWeight: 700,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  padding: '0 4px',
                }}>
                  {unread > 99 ? '99+' : unread}
                </span>
              )}
            </Link>

            {/* Avatar dropdown */}
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => setDropdownOpen(!dropdownOpen)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 8,
                  background: 'none', border: 'none', cursor: 'pointer',
                  padding: '4px 8px', borderRadius: 'var(--radius-md)',
                  transition: 'background var(--transition-fast)',
                }}
                onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                onMouseLeave={e => { if (!dropdownOpen) e.currentTarget.style.background = 'transparent' }}
              >
                <img
                  src={user.avatarUrl}
                  alt={user.username}
                  style={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover' }}
                />
                <div style={{ textAlign: 'left', lineHeight: 1.3 }}>
                  <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-primary)' }}>
                    {user.username}
                  </div>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                    {user.rank} · L{user.level}
                  </div>
                </div>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-muted)" strokeWidth="2">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </button>

              {dropdownOpen && (
                <>
                  <div
                    style={{ position: 'fixed', inset: 0, zIndex: 99 }}
                    onClick={() => setDropdownOpen(false)}
                  />
                  <div style={{
                    position: 'absolute', right: 0, top: '100%', marginTop: 4,
                    minWidth: 200, background: 'var(--color-surface)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    boxShadow: 'var(--shadow-lg)',
                    zIndex: 100,
                    animation: 'fadeIn 100ms ease',
                    overflow: 'hidden',
                  }}>
                    <Link
                      to={`/profile/${user.username}`}
                      onClick={() => setDropdownOpen(false)}
                      style={dropdownItemStyle}
                      onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    >
                      👤 My Profile
                    </Link>
                    <Link
                      to="/projects"
                      onClick={() => setDropdownOpen(false)}
                      style={dropdownItemStyle}
                      onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    >
                      📦 My Projects
                    </Link>
                    <Link
                      to="/settings"
                      onClick={() => setDropdownOpen(false)}
                      style={dropdownItemStyle}
                      onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    >
                      ⚙️ Settings
                    </Link>
                    <div style={{ borderTop: '1px solid var(--color-border)' }} />
                    <button
                      onClick={handleLogout}
                      style={{ ...dropdownItemStyle, width: '100%', border: 'none', background: 'none', cursor: 'pointer', fontFamily: 'inherit', color: 'var(--color-error)' }}
                      onMouseEnter={e => e.currentTarget.style.background = 'var(--color-error-bg)'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    >
                      🚪 Sign Out
                    </button>
                  </div>
                </>
              )}
            </div>
          </>
        ) : (
          <button onClick={handleLogin} style={loginBtnStyle}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/>
            </svg>
            Login with GitHub
          </button>
        )}
      </div>
    </header>
  )
}

const dropdownItemStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '10px 16px',
  fontSize: 'var(--text-sm)', fontWeight: 500,
  color: 'var(--color-text-primary)', textDecoration: 'none',
  transition: 'background var(--transition-fast)',
}

const loginBtnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '8px 18px', borderRadius: 'var(--radius-md)',
  background: 'var(--color-text-primary)', color: '#fff',
  border: 'none', cursor: 'pointer',
  fontSize: 'var(--text-sm)', fontWeight: 600,
  transition: 'opacity var(--transition-fast)',
}
