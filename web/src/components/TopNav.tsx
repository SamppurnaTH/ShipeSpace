import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getGitHubLoginErrorMessage, startGitHubLogin } from '../auth/githubLogin'
import { useUnreadCount } from '../hooks/useUnreadCount'
import { Spinner } from './Spinner'
import { ButtonLoader } from './common/Loading'

const APP_LINKS = [
  { to: '/feed', label: 'Home' },
  { to: '/trending', label: 'Explore' },
  { to: '/projects', label: 'Projects' },
  { to: '/notifications', label: 'Notifications' },
]

const PUBLIC_LINKS = [
  { to: '/trending', label: 'Explore' },
  { to: '/#how-it-works', label: 'How it works' },
  { to: '/search', label: 'Builders' },
]

export function TopNav() {
  const { user, logout, loading } = useAuth()
  const unread = useUnreadCount()
  const location = useLocation()
  const navigate = useNavigate()
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [loginLoading, setLoginLoading] = useState(false)
  const [searchInput, setSearchInput] = useState('')

  const isBanned = Boolean(user?.banned)
  const isAdmin = Boolean(user?.roles?.some(role => role === 'ADMIN' || role === 'ROLE_ADMIN'))

  const handleLogin = async () => {
    setLoginLoading(true)
    try {
      await startGitHubLogin(location.pathname.startsWith('/admin') ? location.pathname : undefined)
    } catch (err) {
      window.alert(getGitHubLoginErrorMessage(err))
      setLoginLoading(false)
    }
  }

  const closeMenus = () => {
    setUserMenuOpen(false)
    setMobileMenuOpen(false)
  }

  const handleCreateShip = () => {
    if (isBanned) return
    closeMenus()
    navigate('/ships/new')
  }

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const query = searchInput.trim()
    if (!query) return
    closeMenus()
    navigate(`/search?q=${encodeURIComponent(query)}`)
  }

  return (
    <header className="top-nav">
      <div className="top-nav-inner">
        <Link to={user ? '/feed' : '/'} className="top-nav-brand" aria-label="ShipSpace home" onClick={closeMenus}>
          <span className="top-nav-mark"><ShipSpaceMark /></span>
          <span className="top-nav-brand-copy">
            <strong>ShipSpace</strong>
            <small>Ship in public</small>
          </span>
        </Link>

        <nav className="top-nav-links" aria-label={user ? 'Workspace navigation' : 'Public navigation'}>
          {user ? (
            <>
              {APP_LINKS.map(link => (
                <TopNavLink
                  key={link.to}
                  to={link.to}
                  label={link.label}
                  active={isActive(location.pathname, link.to)}
                  badge={link.to === '/notifications' ? unread : 0}
                />
              ))}
              {isAdmin && (
                <TopNavLink
                  to="/admin"
                  label="Admin"
                  active={isActive(location.pathname, '/admin')}
                />
              )}
            </>
          ) : (
            <>
              {PUBLIC_LINKS.map(link => (
                <TopNavLink key={link.to} to={link.to} label={link.label} active={isActive(location.pathname, link.to)} />
              ))}
              <button type="button" className="top-nav-link top-nav-link-button" onClick={handleLogin}>GitHub</button>
            </>
          )}
        </nav>

        {user && (
          <form className="top-nav-search" role="search" onSubmit={handleSearch}>
            <label htmlFor="top-nav-search-input">Search ShipSpace</label>
            <input
              id="top-nav-search-input"
              value={searchInput}
              onChange={event => setSearchInput(event.target.value)}
              placeholder="Search builders, projects, ships..."
            />
          </form>
        )}

        <div className="top-nav-actions">
          {loading ? (
            <Spinner size={16} />
          ) : user ? (
            <>
              {isBanned ? (
                <span className="top-nav-status">Writes blocked</span>
              ) : (
                <button type="button" className="top-nav-primary" onClick={handleCreateShip}>
                  Create Ship
                </button>
              )}

              <div className="top-nav-user">
                <button
                  type="button"
                  className="top-nav-avatar-button"
                  aria-label="Open profile menu"
                  aria-expanded={userMenuOpen}
                  onClick={() => setUserMenuOpen(open => !open)}
                >
                  <img src={user.avatarUrl} alt={`${user.username} avatar`} />
                  <span>{user.username}</span>
                  <ChevronDownIcon />
                </button>

                {userMenuOpen && (
                  <>
                    <button className="top-nav-menu-scrim" type="button" aria-label="Close profile menu" onClick={() => setUserMenuOpen(false)} />
                    <div className="top-nav-user-menu">
                      <div className="top-nav-user-summary">
                        <img src={user.avatarUrl} alt="" />
                        <div>
                          <strong>{user.username}</strong>
                          <span>{user.rank} · Level {user.level}</span>
                        </div>
                      </div>
                      {isAdmin && <Link to="/admin" onClick={closeMenus}>Admin Console</Link>}
                      {isBanned && <span className="top-nav-menu-status">Account writes blocked</span>}
                      <Link to={`/profile/${user.username}`} onClick={closeMenus}>View Profile</Link>
                      <Link to="/projects" onClick={closeMenus}>Projects</Link>
                      <Link to="/settings" onClick={closeMenus}>Settings</Link>
                      <button type="button" onClick={async () => { closeMenus(); await logout() }}>Logout</button>
                    </div>
                  </>
                )}
              </div>
            </>
          ) : (
            <>
              <Link to="/auth?mode=signin" className="top-nav-secondary">Sign in</Link>
              <button type="button" className="top-nav-primary" onClick={handleLogin} disabled={loginLoading}>
                {loginLoading ? (
                  <ButtonLoader label="Connecting..." />
                ) : (
                  <>
                    <GitHubIcon />
                    Continue with GitHub
                  </>
                )}
              </button>
            </>
          )}
        </div>

        <button
          type="button"
          className="top-nav-mobile-toggle"
          aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
          aria-controls="top-nav-mobile-menu"
          aria-expanded={mobileMenuOpen}
          onClick={() => setMobileMenuOpen(open => !open)}
        >
          <span />
          <span />
          <span />
        </button>
      </div>

      {mobileMenuOpen && (
        <div className="top-nav-mobile-menu" id="top-nav-mobile-menu">
          {user ? (
            <>
              {APP_LINKS.map(link => (
                <Link key={link.to} to={link.to} onClick={closeMenus}>{link.label}</Link>
              ))}
              {isAdmin && <Link to="/admin" onClick={closeMenus}>Admin Console</Link>}
              {!isBanned && <button type="button" onClick={handleCreateShip}>Create Ship</button>}
              {isBanned && <span className="top-nav-mobile-status">Writes blocked</span>}
              <Link to={`/profile/${user.username}`} onClick={closeMenus}>Profile</Link>
              <button type="button" onClick={async () => { closeMenus(); await logout() }}>Logout</button>
            </>
          ) : (
            <>
              <Link to="/trending" onClick={closeMenus}>Explore</Link>
              <Link to="/#how-it-works" onClick={closeMenus}>How it works</Link>
              <Link to="/search" onClick={closeMenus}>Builders</Link>
              <button type="button" onClick={() => { closeMenus(); void handleLogin() }}>GitHub</button>
              <Link to="/auth?mode=signin" onClick={closeMenus}>Sign in</Link>
              <Link to="/signup?mode=signup" onClick={closeMenus}>Sign up</Link>
              <button type="button" className="top-nav-primary" onClick={() => { closeMenus(); void handleLogin() }} disabled={loginLoading}>
                {loginLoading ? (
                  <ButtonLoader label="Connecting..." />
                ) : (
                  <>
                    <GitHubIcon />
                    Continue with GitHub
                  </>
                )}
              </button>
            </>
          )}
        </div>
      )}
    </header>
  )
}

function TopNavLink({ to, label, active, badge = 0 }: { to: string; label: string; active: boolean; badge?: number }) {
  return (
    <Link to={to} className={`top-nav-link ${active ? 'top-nav-link-active' : ''}`}>
      <span>{label}</span>
      {badge > 0 && <strong aria-label={`${badge} unread notifications`}>{badge > 9 ? '9+' : badge}</strong>}
    </Link>
  )
}

function isActive(pathname: string, to: string) {
  if (to === '/feed') return pathname === '/feed' || pathname.startsWith('/feed/')
  return pathname === to || pathname.startsWith(`${to}/`)
}

function ShipSpaceMark() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M5 15.5 12 4l7 11.5" />
      <path d="M8 15.5h8" />
      <path d="M6.5 19h11" />
      <path d="M12 4v15" />
    </svg>
  )
}

function GitHubIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.44 9.8 8.21 11.39.6.11.82-.26.82-.58 0-.28-.01-1.03-.02-2.02-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.75.08-.73.08-.73 1.2.08 1.84 1.24 1.84 1.24 1.07 1.83 2.81 1.3 3.49.99.11-.78.42-1.3.76-1.6-2.66-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.12-.3-.54-1.52.12-3.18 0 0 1.01-.32 3.3 1.23A11.5 11.5 0 0 1 12 5.31c1.02.01 2.05.14 3.01.41 2.29-1.55 3.3-1.23 3.3-1.23.66 1.66.24 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.62-5.48 5.92.43.37.81 1.1.81 2.22 0 1.6-.01 2.89-.01 3.28 0 .32.22.69.83.57A12.01 12.01 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
    </svg>
  )
}

function ChevronDownIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M7 10l5 5 5-5z" />
    </svg>
  )
}

