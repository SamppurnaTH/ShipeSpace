import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useUnreadCount } from '../hooks/useUnreadCount'
import { Icon } from './Icon'

export function BottomNav() {
  const location = useLocation()
  const { user } = useAuth()
  const unread = useUnreadCount()

  const items = [
    { icon: 'home' as const, label: 'Feed', path: '/feed' },
    { icon: 'trending' as const, label: 'Trending', path: '/trending' },
    { icon: 'box' as const, label: 'Projects', path: '/projects' },
    { icon: 'network' as const, label: 'Network', path: '/collaborations' },
    { icon: 'user' as const, label: 'Me', path: user ? `/profile/${user.username}` : '/auth' },
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
      boxShadow: '0 -1px 0 var(--color-border)',
    }}>
      {items.map(item => {
        const active = location.pathname === item.path
        return (
          <Link key={item.path} to={item.path} style={{
            position: 'relative',
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            gap: 2, textDecoration: 'none',
            fontSize: 10, fontWeight: active ? 600 : 500,
            color: active ? 'var(--color-brand)' : 'var(--color-text-muted)',
            padding: '6px 12px',
            transition: 'color var(--transition-fast)',
          }}>
            <span style={{ position: 'relative', display: 'inline-flex' }}>
              <Icon name={item.icon} size={20} />
              {item.path === '/notifications' && unread > 0 && (
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
