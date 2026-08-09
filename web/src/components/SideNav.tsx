import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useUnreadCount } from '../hooks/useUnreadCount'
import { Icon } from './Icon'

const NAV_ITEMS = [
  { icon: 'home' as const, label: 'Feed', path: '/feed' },
  { icon: 'trending' as const, label: 'Trending', path: '/trending' },
  { icon: 'box' as const, label: 'Projects', path: '/projects' },
  { icon: 'bell' as const, label: 'Notifications', path: '/notifications' },
]

export function SideNav() {
  const location = useLocation()
  const { user } = useAuth()
  const unread = useUnreadCount()

  return (
    <aside style={{
      width: 'var(--sidebar-width)',
      position: 'sticky',
      top: 64,
      height: 'calc(100vh - 64px)',
      borderRight: '1px solid var(--color-border)',
      background: 'var(--color-surface)',
      padding: 'var(--space-4) 0',
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      overflowY: 'auto',
      flexShrink: 0,
    }}>
      {NAV_ITEMS.map(item => {
        const active = location.pathname === item.path ||
          (item.path === '/feed' && location.pathname === '/feed/personalized')
        return (
          <Link
            key={item.path}
            to={item.path}
            style={{
              display: 'flex', alignItems: 'center', gap: 12,
              padding: '10px var(--space-5)',
              margin: '0 var(--space-2)',
              borderRadius: 'var(--radius-md)',
              textDecoration: 'none',
              fontSize: 'var(--text-sm)',
              fontWeight: active ? 600 : 500,
              color: active ? 'var(--color-brand)' : 'var(--color-text-secondary)',
              background: active ? 'var(--color-brand-light)' : 'transparent',
              borderLeft: active ? '3px solid var(--color-brand)' : '3px solid transparent',
              transition: 'all var(--transition-fast)',
            }}
            onMouseEnter={e => {
              if (!active) {
                e.currentTarget.style.background = 'var(--color-surface-2)'
                e.currentTarget.style.color = 'var(--color-text-primary)'
              }
            }}
            onMouseLeave={e => {
              if (!active) {
                e.currentTarget.style.background = 'transparent'
                e.currentTarget.style.color = 'var(--color-text-secondary)'
              }
            }}
          >
            <span style={{ width: 24, display: 'inline-flex', justifyContent: 'center' }}>
              <Icon name={item.icon} size={18} />
            </span>
            {item.label}
            {item.path === '/notifications' && unread > 0 && (
              <span style={{
                marginLeft: 'auto',
                minWidth: 18, height: 18,
                borderRadius: 'var(--radius-full)',
                background: 'var(--color-error)',
                color: '#fff', fontSize: 10, fontWeight: 700,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                padding: '0 4px',
              }}>
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </Link>
        )
      })}

      <div style={{
        borderTop: '1px solid var(--color-border)',
        margin: 'var(--space-4) var(--space-4)',
      }} />

      {user && (
        <>
          <Link
            to={`/profile/${user.username}`}
            style={{
              ...navItemBase,
              color: location.pathname.startsWith('/profile') ? 'var(--color-brand)' : 'var(--color-text-secondary)',
              background: location.pathname.startsWith('/profile') ? 'var(--color-brand-light)' : 'transparent',
              borderLeft: location.pathname.startsWith('/profile') ? '3px solid var(--color-brand)' : '3px solid transparent',
            }}
            onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-surface-2)' }}
            onMouseLeave={e => {
              e.currentTarget.style.background = location.pathname.startsWith('/profile') ? 'var(--color-brand-light)' : 'transparent'
            }}
          >
            <span style={{ width: 24, display: 'inline-flex', justifyContent: 'center' }}><Icon name="user" size={18} /></span>
            My Profile
          </Link>
          <Link
            to="/settings"
            style={{
              ...navItemBase,
              color: location.pathname === '/settings' ? 'var(--color-brand)' : 'var(--color-text-secondary)',
              background: location.pathname === '/settings' ? 'var(--color-brand-light)' : 'transparent',
              borderLeft: location.pathname === '/settings' ? '3px solid var(--color-brand)' : '3px solid transparent',
            }}
            onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-surface-2)' }}
            onMouseLeave={e => {
              e.currentTarget.style.background = location.pathname === '/settings' ? 'var(--color-brand-light)' : 'transparent'
            }}
          >
            <span style={{ width: 24, display: 'inline-flex', justifyContent: 'center' }}><Icon name="settings" size={18} /></span>
            Settings
          </Link>
        </>
      )}

      {user && !user.banned && (
        <div style={{ marginTop: 'auto', padding: 'var(--space-4)' }}>
          <Link
            to="/ships/new"
            style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              padding: '10px 16px',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-brand)',
              color: '#fff',
              fontWeight: 600,
              fontSize: 'var(--text-sm)',
              textDecoration: 'none',
              transition: 'all var(--transition-fast)',
              boxShadow: '0 2px 8px rgba(99,102,241,0.3)',
            }}
            onMouseEnter={e => {
              e.currentTarget.style.background = 'var(--color-brand-hover)'
              e.currentTarget.style.transform = 'translateY(-1px)'
            }}
            onMouseLeave={e => {
              e.currentTarget.style.background = 'var(--color-brand)'
              e.currentTarget.style.transform = 'none'
            }}
          >
            <Icon name="ship" size={16} /> Ship Something
          </Link>
        </div>
      )}
    </aside>
  )
}

const navItemBase: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 12,
  padding: '10px var(--space-5)',
  margin: '0 var(--space-2)',
  borderRadius: 'var(--radius-md)',
  textDecoration: 'none',
  fontSize: 'var(--text-sm)',
  fontWeight: 500,
  transition: 'all var(--transition-fast)',
}
