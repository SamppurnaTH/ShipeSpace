import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useUnreadCount } from '../hooks/useUnreadCount'

export function BottomNav() {
  const location = useLocation()
  const { user } = useAuth()
  const unread = useUnreadCount()

  const items = [
    { icon: '⚡', label: 'Feed',       path: '/feed' },
    { icon: '🔥', label: 'Trending',   path: '/trending' },
    { icon: '📦', label: 'Projects',   path: '/projects' },
    { icon: '🔔', label: 'Alerts',     path: '/notifications' },
    { icon: '👤', label: 'Profile',    path: user ? `/profile/${user.username}` : '/auth' },
  ]

  return (
    <nav className="bottom-nav-mobile" style={{
      position: 'fixed', bottom: 0, left: 0, right: 0,
      height: 56,
      background: 'var(--color-surface)',
      borderTop: '1px solid var(--color-border)',
      display: 'none',
      alignItems: 'center', justifyContent: 'space-around',
      zIndex: 100,
    }}>
      {items.map(item => {
        const active = location.pathname === item.path
        const isNotif = item.path === '/notifications'
        return (
          <Link key={item.path} to={item.path} style={{
            position: 'relative',
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            gap: 2, textDecoration: 'none',
            fontSize: 10, fontWeight: active ? 600 : 500,
            color: active ? 'var(--color-brand-dim)' : 'var(--color-text-muted)',
            padding: '6px 12px',
            transition: 'color var(--transition-fast)',
          }}>
            <span style={{ fontSize: 20, position: 'relative' }}>
              {item.icon}
              {isNotif && unread > 0 && (
                <span style={{
                  position: 'absolute', top: -4, right: -6,
                  minWidth: 14, height: 14,
                  borderRadius: 'var(--radius-full)',
                  background: 'var(--color-error)',
                  color: '#fff', fontSize: 9, fontWeight: 700,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  padding: '0 3px',
                }}>
                  {unread > 9 ? '9+' : unread}
                </span>
              )}
            </span>
            {item.label}
          </Link>
        )
      })}
      <style>{`@media (max-width: 768px) { .bottom-nav-mobile { display: flex !important; } }`}</style>
    </nav>
  )
}