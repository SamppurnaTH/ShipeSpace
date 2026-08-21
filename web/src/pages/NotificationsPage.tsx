import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Spinner } from '../components/Spinner'
import { toastError, toastSuccess } from '../components/Toast'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../api/endpoints'
import { Icon } from '../components/Icon'
import { UNREAD_COUNT_REFRESH_EVENT } from '../hooks/useUnreadCount'

type NotificationItem = {
  id: string
  type: string
  text: string
  actorId?: string
  actorAvatarUrl?: string
  targetId?: string
  targetView?: string
  read: boolean
  createdAt: string
}

const TYPE_ICONS: Record<string, Parameters<typeof Icon>[0]['name']> = {
  FOLLOW: 'user',
  LIKE: 'thumb',
  COMMENT: 'comment',
  COLLABORATION_REQUESTED: 'network',
  COLLABORATION_ACCEPTED: 'network',
  COLLABORATION_DECLINED: 'network',
  MENTION: 'info',
}

const TYPE_LABELS: Record<string, string> = {
  FOLLOW: 'Follow',
  LIKE: 'Ship like',
  COMMENT: 'Ship comment',
  COLLABORATION_REQUESTED: 'Collaboration request',
  COLLABORATION_ACCEPTED: 'Collaboration accepted',
  COLLABORATION_DECLINED: 'Collaboration declined',
  MENTION: 'Mention',
}

const POLL_INTERVAL_MS = 30_000

function timeAgo(iso: string) {
  const delta = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(delta / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

function destinationFor(notification: NotificationItem) {
  if (notification.targetView?.startsWith('/')) return notification.targetView
  if (notification.type === 'FOLLOW' && notification.actorId) return `/profile/${notification.actorId}`
  if ((notification.type === 'LIKE' || notification.type === 'COMMENT') && notification.targetId) return `/ships/${notification.targetId}`
  if (notification.type.startsWith('COLLABORATION_')) return '/collaborations'
  return null
}

function refreshUnreadBadges() {
  window.dispatchEvent(new Event(UNREAD_COUNT_REFRESH_EVENT))
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set())
  const [markingAll, setMarkingAll] = useState(false)
  const navigate = useNavigate()
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchNotifications = useCallback((showLoader = false) => {
    if (showLoader) setLoading(true)
    setError(null)
    return getNotifications()
      .then(response => setNotifications(response.data.content ?? []))
      .catch(() => {
        if (showLoader) setError('Could not load notifications.')
      })
      .finally(() => {
        if (showLoader) setLoading(false)
      })
  }, [])

  useEffect(() => {
    fetchNotifications(true)
    pollRef.current = setInterval(() => fetchNotifications(false), POLL_INTERVAL_MS)
    return () => { if (pollRef.current) clearInterval(pollRef.current) }
  }, [fetchNotifications])

  const markRead = async (notification: NotificationItem) => {
    if (notification.read || pendingIds.has(notification.id)) return true
    setPendingIds(prev => new Set(prev).add(notification.id))
    try {
      const response = await markNotificationRead(notification.id)
      setNotifications(prev => prev.map(item => item.id === notification.id ? { ...item, ...response.data } : item))
      refreshUnreadBadges()
      return true
    } catch {
      toastError('Could not mark notification read.')
      await fetchNotifications(false)
      return false
    } finally {
      setPendingIds(prev => {
        const next = new Set(prev)
        next.delete(notification.id)
        return next
      })
    }
  }

  const handleOpen = async (notification: NotificationItem) => {
    const destination = destinationFor(notification)
    const readOk = await markRead(notification)
    if (destination && readOk) navigate(destination)
  }

  const handleMarkAll = async () => {
    if (markingAll) return
    setMarkingAll(true)
    try {
      await markAllNotificationsRead()
      setNotifications(prev => prev.map(item => ({ ...item, read: true })))
      refreshUnreadBadges()
      toastSuccess('All notifications marked as read')
    } catch {
      toastError('Could not mark notifications read')
      await fetchNotifications(false)
    } finally {
      setMarkingAll(false)
    }
  }

  const unreadCount = notifications.filter(notification => !notification.read).length

  return (
    <div style={{ maxWidth: 700, margin: '0 auto', padding: '0 var(--space-4)' }}>
      <header style={headerStyle}>
        <div>
          <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 800, color: 'var(--color-text-primary)', margin: 0 }}>
            Notifications
          </h1>
          <p style={{ margin: 'var(--space-2) 0 0', color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
            Follow builder activity, ship engagement, and collaboration decisions.
          </p>
        </div>
        {unreadCount > 0 && (
          <button
            type="button"
            onClick={handleMarkAll}
            disabled={markingAll}
            style={markAllStyle(markingAll)}
          >
            {markingAll ? 'Saving...' : 'Mark all read'}
          </button>
        )}
      </header>

      {loading && (
        <div aria-busy="true" style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
          <Spinner size={28} />
        </div>
      )}

      {!loading && error && (
        <div role="alert" style={alertStyle}>
          {error}
          <button type="button" onClick={() => fetchNotifications(true)} style={retryStyle}>Retry</button>
        </div>
      )}

      {!loading && !error && notifications.length === 0 && (
        <div style={emptyStyle}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-4)' }}>
            <Icon name="bell" size={40} />
          </div>
          <div style={{ fontWeight: 700, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>
            No notifications yet
          </div>
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', lineHeight: 1.5 }}>
            When builders follow you, engage with your ships, or respond to collaboration requests, you will see it here.
          </div>
        </div>
      )}

      {!loading && !error && notifications.length > 0 && (
        <section aria-label="Notification list" style={listStyle}>
          {notifications.map((notification, index) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              divider={index < notifications.length - 1}
              pending={pendingIds.has(notification.id)}
              onOpen={() => handleOpen(notification)}
            />
          ))}
        </section>
      )}
    </div>
  )
}

function NotificationRow({ notification, divider, pending, onOpen }: {
  notification: NotificationItem
  divider: boolean
  pending: boolean
  onOpen: () => void
}) {
  const destination = destinationFor(notification)

  return (
    <button
      type="button"
      onClick={onOpen}
      disabled={pending}
      aria-label={`${TYPE_LABELS[notification.type] ?? 'Notification'}: ${notification.text}${notification.read ? '' : '. Unread'}`}
      style={{
        ...rowStyle,
        borderBottom: divider ? '1px solid var(--color-border)' : 'none',
        background: notification.read ? 'transparent' : 'var(--color-brand-light)',
        borderLeft: notification.read ? '3px solid transparent' : '3px solid var(--color-brand)',
        cursor: pending ? 'wait' : destination ? 'pointer' : 'default',
      }}
    >
      {notification.actorAvatarUrl ? (
        <img src={notification.actorAvatarUrl} alt="" style={avatarStyle} />
      ) : (
        <span aria-hidden="true" style={iconFallbackStyle}>
          <Icon name={TYPE_ICONS[notification.type] ?? 'bell'} size={18} />
        </span>
      )}
      <span style={{ flex: 1, minWidth: 0, textAlign: 'left' }}>
        <span style={typeLabelStyle}>{TYPE_LABELS[notification.type] ?? 'Notification'}</span>
        <span style={textStyle}>{notification.text}</span>
        <span style={metaStyle}>{timeAgo(notification.createdAt)}</span>
      </span>
      {!notification.read && (
        <span style={unreadLabelStyle}>
          <span aria-hidden="true" style={unreadDotStyle} />
          Unread
        </span>
      )}
    </button>
  )
}

const headerStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  justifyContent: 'space-between',
  gap: 'var(--space-4)',
  marginBottom: 'var(--space-5)',
  flexWrap: 'wrap',
}

const listStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  borderRadius: 'var(--radius-lg)',
  border: '1px solid var(--color-border)',
  overflow: 'hidden',
}

const rowStyle: React.CSSProperties = {
  width: '100%',
  display: 'flex',
  gap: 'var(--space-3)',
  alignItems: 'flex-start',
  padding: 'var(--space-4)',
  border: 'none',
  color: 'inherit',
  fontFamily: 'inherit',
}

const avatarStyle: React.CSSProperties = {
  width: 38,
  height: 38,
  borderRadius: '50%',
  objectFit: 'cover',
  flexShrink: 0,
}

const iconFallbackStyle: React.CSSProperties = {
  width: 38,
  height: 38,
  borderRadius: '50%',
  flexShrink: 0,
  background: 'var(--color-surface-3)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: 'var(--color-text-muted)',
}

const typeLabelStyle: React.CSSProperties = {
  display: 'block',
  color: 'var(--color-text-muted)',
  fontSize: 'var(--text-xs)',
  fontWeight: 800,
  fontFamily: 'var(--font-mono)',
  marginBottom: 2,
}

const textStyle: React.CSSProperties = {
  display: 'block',
  fontSize: 'var(--text-sm)',
  color: 'var(--color-text-primary)',
  lineHeight: 1.5,
  overflowWrap: 'anywhere',
}

const metaStyle: React.CSSProperties = {
  display: 'block',
  fontSize: 'var(--text-xs)',
  color: 'var(--color-text-muted)',
  marginTop: 3,
  fontFamily: 'var(--font-mono)',
}

const unreadLabelStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 5,
  color: 'var(--color-brand-dim)',
  fontSize: 'var(--text-xs)',
  fontWeight: 800,
  flexShrink: 0,
}

const unreadDotStyle: React.CSSProperties = {
  width: 8,
  height: 8,
  borderRadius: '50%',
  background: 'var(--color-brand)',
}

const emptyStyle: React.CSSProperties = {
  textAlign: 'center',
  padding: 'var(--space-12)',
  color: 'var(--color-text-muted)',
  background: 'var(--color-surface)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-lg)',
}

const alertStyle: React.CSSProperties = {
  padding: 'var(--space-4)',
  border: '1px solid var(--color-error)',
  borderRadius: 'var(--radius-lg)',
  background: 'var(--color-error-bg)',
  color: 'var(--color-error)',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  gap: 'var(--space-3)',
}

const retryStyle: React.CSSProperties = {
  border: '1px solid currentColor',
  color: 'inherit',
  background: 'transparent',
  borderRadius: 'var(--radius-md)',
  padding: '6px 12px',
  cursor: 'pointer',
}

function markAllStyle(disabled: boolean): React.CSSProperties {
  return {
    background: disabled ? 'var(--color-surface-2)' : 'var(--color-surface)',
    border: '1px solid var(--color-border)',
    borderRadius: 'var(--radius-md)',
    padding: '8px 14px',
    fontSize: 'var(--text-sm)',
    color: disabled ? 'var(--color-text-muted)' : 'var(--color-text-secondary)',
    cursor: disabled ? 'wait' : 'pointer',
    fontFamily: 'inherit',
    fontWeight: 700,
  }
}
