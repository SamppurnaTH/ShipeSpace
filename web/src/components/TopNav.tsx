import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useUnreadCount } from '../hooks/useUnreadCount'
import { Icon } from './Icon'
import { Spinner } from './Spinner'

const PRIMARY_NAV_LINKS = [
  { to: '/trending', label: 'Top Content' },
  { to: '/people', label: 'People' },
  { to: '/learning', label: 'Learning' },
  { to: '/projects', label: 'Projects' },
  { to: '/shipscore', label: 'ShipScore' },
]

export function TopNav() {
  const { user, logout, loading } = useAuth()
  const unread = useUnreadCount()
  const location = useLocation()
  const navigate = useNavigate()
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [searchInput, setSearchInput] = useState('')

  const isBanned = Boolean(user?.banned)
  const isAdmin = Boolean(user?.roles?.some(role => role === 'ADMIN' || role === 'ROLE_ADMIN'))

  const handleSignup = () => {
    closeMenus()
    navigate('/auth?mode=signup')
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
        <Link to={user ? '/feed' : '/'} className="top-nav-brand" aria-label="KREVO home" onClick={closeMenus}>
          <span className="top-nav-mark"><ShipSpaceMark /></span>
        </Link>

        <nav className="top-nav-links" aria-label="Primary navigation">
          {PRIMARY_NAV_LINKS.map(link => (
            <TopNavLink
              key={link.to}
              to={link.to}
              label={link.label}
              active={isActive(location.pathname, link.to)}
            />
          ))}
          {user && isAdmin && (
            <TopNavLink
              to="/admin"
              label="Admin"
              active={isActive(location.pathname, '/admin')}
            />
          )}
        </nav>

        <div className="top-nav-actions">
          {loading ? (
            <Spinner size={16} />
          ) : user ? (
            <>
              <form className="top-nav-search" role="search" onSubmit={handleSearch}>
                <label htmlFor="top-nav-search-input">Search builders and ships</label>
                <input
                  id="top-nav-search-input"
                  value={searchInput}
                  onChange={event => setSearchInput(event.target.value)}
                  placeholder="Search builders, projects, ships..."
                />
              </form>

              {isBanned ? (
                <span className="top-nav-status">Writes blocked</span>
              ) : (
                <button type="button" className="top-nav-primary" onClick={handleCreateShip}>
                  Create Ship
                </button>
              )}

              <Link to="/notifications" className="top-nav-notifications-icon" aria-label={unread > 0 ? `${unread} unread notifications` : 'Notifications'}>
                <Icon name="bell" size={20} />
                {unread > 0 && (
                  <span className="top-nav-notifications-badge">{unread > 9 ? '9+' : unread}</span>
                )}
              </Link>

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
                      <Link to={`/profile/${user.id}`} onClick={closeMenus}>View Profile</Link>
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
              <button type="button" className="top-nav-primary" onClick={handleSignup}>
                <GitHubIcon />
                Continue with GitHub
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
          {PRIMARY_NAV_LINKS.map(link => (
            <Link key={link.to} to={link.to} onClick={closeMenus}>{link.label}</Link>
          ))}
          {user ? (
            <>
              <Link to="/notifications" onClick={closeMenus}>Notifications{unread > 0 ? ` (${unread})` : ''}</Link>
              {isAdmin && <Link to="/admin" onClick={closeMenus}>Admin Console</Link>}
              {!isBanned && <button type="button" onClick={handleCreateShip}>Create Ship</button>}
              {isBanned && <span className="top-nav-mobile-status">Writes blocked</span>}
              <Link to={`/profile/${user.id}`} onClick={closeMenus}>Profile</Link>
              <button type="button" onClick={async () => { closeMenus(); await logout() }}>Logout</button>
            </>
          ) : (
            <>
              <Link to="/auth?mode=signin" onClick={closeMenus}>Sign in</Link>
              <button type="button" className="top-nav-primary" onClick={handleSignup}>
                <GitHubIcon />
                Continue with GitHub
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
  return <img src="/logo.png" alt="KREVO Logo" />
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



