import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Spinner } from '../components/Spinner'
import { toastError, toastSuccess } from '../components/Toast'
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../api/endpoints'
import { Icon } from '../components/Icon'

const TYPE_ICONS: Record<string, Parameters<typeof Icon>[0]['name']> = {
  SHIP_LIKE: 'thumb',
  SHIP_COMMENT: 'comment',
  NEW_FOLLOWER: 'user',
  MENTION: 'info',
}

const POLL_INTERVAL_MS = 30_000

function timeAgo(iso: string) {
  const d = Date.now() - new Date(iso).getTime()
  const m = Math.floor(d / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchNotifications = useCallback((showLoader = false) => {
    if (showLoader) setLoading(true)
    return getNotifications()
      .then(r => setNotifications(r.data.content ?? []))
      .catch(() => { if (showLoader) toastError('Could not load notifications') })
      .finally(() => { if (showLoader) setLoading(false) })
  }, [])

  useEffect(() => {
    fetchNotifications(true)
    pollRef.current = setInterval(() => fetchNotifications(false), POLL_INTERVAL_MS)
    return () => { if (pollRef.current) clearInterval(pollRef.current) }
  }, [fetchNotifications])

  const handleClick = async (n: any) => {
    if (!n.read) {
      await markNotificationRead(n.id).catch(() => {})
      setNotifications(p => p.map(x => x.id === n.id ? { ...x, read: true } : x))
    }
    if (n.targetView) navigate(n.targetView)
  }

  const handleMarkAll = async () => {
    await markAllNotificationsRead().catch(() => toastError('Could not mark notifications read'))
    setNotifications(p => p.map(x => ({ ...x, read: true })))
    toastSuccess('All notifications marked as read')
  }

  const unreadCount = notifications.filter(n => !n.read).length

  return (
    <div style={{ maxWidth: 680, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-5)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, color: 'var(--color-text-primary)' }}>
          Notifications
          {unreadCount > 0 && (
            <span style={{
              marginLeft: 8, fontSize: 'var(--text-xs)', fontFamily: 'var(--font-mono)',
              background: 'var(--color-brand)', color: '#fff',
              borderRadius: 'var(--radius-full)', padding: '2px 7px',
            }}>{unreadCount}</span>
          )}
        </h1>
        {unreadCount > 0 && (
          <button onClick={handleMarkAll} style={{
            background: 'none', border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)', padding: '6px 14px',
            fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)',
            cursor: 'pointer', fontFamily: 'inherit',
            transition: 'all var(--transition-fast)',
          }}>Mark all read</button>
        )}
      </div>

      {loading && <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}><Spinner size={28} /></div>}

      {!loading && notifications.length === 0 && (
        <div style={{ textAlign: 'center', padding: 'var(--space-12)', color: 'var(--color-text-muted)' }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-4)' }}>
            <Icon name="bell" size={40} />
          </div>
          <div style={{ fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>No notifications yet</div>
          <div style={{ fontSize: 'var(--text-sm)' }}>When someone likes or comments on your ships, you'll see it here.</div>
        </div>
      )}

      <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {notifications.map((n, i) => (
          <div key={n.id} onClick={() => handleClick(n)} style={{
            display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start',
            padding: 'var(--space-4)',
            borderBottom: i < notifications.length - 1 ? '1px solid var(--color-border)' : 'none',
            background: n.read ? 'transparent' : 'var(--color-brand-light)',
            borderLeft: n.read ? 'none' : '3px solid var(--color-brand)',
            cursor: n.targetView ? 'pointer' : 'default',
            transition: 'background var(--transition-fast)',
          }}
            onMouseEnter={e => { if (n.targetView) e.currentTarget.style.background = 'var(--color-surface-2)' }}
            onMouseLeave={e => { e.currentTarget.style.background = n.read ? 'transparent' : 'var(--color-brand-light)' }}
          >
            {n.actorAvatarUrl ? (
              <img src={n.actorAvatarUrl} alt="" style={{ width: 36, height: 36, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
            ) : (
              <div style={{
                width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                background: 'var(--color-surface-3)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'var(--color-text-muted)',
              }}><Icon name={TYPE_ICONS[n.type] ?? 'bell'} size={18} /></div>
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)', lineHeight: 1.5 }}>
                {n.text}
              </div>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 3, fontFamily: 'var(--font-mono)' }}>
                {timeAgo(n.createdAt)}
              </div>
            </div>
            {!n.read && (
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--color-brand)', flexShrink: 0, marginTop: 4 }} />
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
