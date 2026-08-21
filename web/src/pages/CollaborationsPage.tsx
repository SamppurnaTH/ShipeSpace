import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { acceptCollaboration, declineCollaboration, getIncomingCollabs, getSentCollabs } from '../api/endpoints'
import { Spinner } from '../components/Spinner'
import { toastError, toastSuccess } from '../components/Toast'
import { Icon } from '../components/Icon'

type Tab = 'incoming' | 'sent'
type CollaborationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED'

type Collaboration = {
  id: string
  requesterId: string
  ownerId: string
  projectId: string
  message?: string
  status: CollaborationStatus
  createdAt?: string
  resolvedAt?: string
  requester?: { id: string; username: string; avatarUrl?: string }
  owner?: { id: string; username: string; avatarUrl?: string }
  project?: { id: string; name: string; userId: string }
}

const STATUS_COLORS: Record<CollaborationStatus, string> = {
  PENDING: 'var(--color-warning)',
  ACCEPTED: 'var(--color-success)',
  DECLINED: 'var(--color-text-muted)',
}

const STATUS_LABELS: Record<CollaborationStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined',
}

function timeAgo(iso?: string) {
  if (!iso) return 'unknown'
  const delta = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(delta / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

export function CollaborationsPage() {
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('incoming')
  const [incoming, setIncoming] = useState<Collaboration[]>([])
  const [sent, setSent] = useState<Collaboration[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pendingActionId, setPendingActionId] = useState<string | null>(null)
  const isBanned = Boolean(user?.banned)

  const fetchAll = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [incomingResponse, sentResponse] = await Promise.all([
        getIncomingCollabs(),
        getSentCollabs(),
      ])
      setIncoming(incomingResponse.data.content ?? incomingResponse.data ?? [])
      setSent(sentResponse.data.content ?? sentResponse.data ?? [])
    } catch {
      setError('Could not load collaboration requests.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])

  const handleAction = async (collaboration: Collaboration, action: 'accept' | 'decline') => {
    if (isBanned) {
      toastError('Writes are blocked for this account.')
      return
    }
    setPendingActionId(collaboration.id)
    try {
      const response = action === 'accept'
        ? await acceptCollaboration(collaboration.id)
        : await declineCollaboration(collaboration.id)
      const updated = response.data
      setIncoming(prev => prev.map(item => item.id === collaboration.id ? { ...item, ...updated } : item))
      toastSuccess(action === 'accept' ? 'Collaboration accepted' : 'Request declined')
    } catch (err: any) {
      const message = err?.response?.data?.message ?? 'Action failed. Try again.'
      toastError(message)
      await fetchAll()
    } finally {
      setPendingActionId(null)
    }
  }

  const pendingIncomingCount = incoming.filter(item => item.status === 'PENDING').length
  const pendingSentCount = sent.filter(item => item.status === 'PENDING').length

  return (
    <div style={{ maxWidth: 780, margin: '0 auto', padding: '0 var(--space-4)' }}>
      <header style={headerStyle}>
        <div>
          <p style={eyebrowStyle}>BUILD TOGETHER</p>
          <h1 style={{ fontSize: 'var(--text-2xl)', fontWeight: 800, margin: 0 }}>Collaborations</h1>
          <p style={{ margin: 'var(--space-2) 0 0', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
            Review requests connected to real projects. Start a new request from a project page so the context is always clear.
          </p>
        </div>
        <Link to="/projects" style={secondaryLinkStyle}>
          <Icon name="search" size={16} /> Find projects
        </Link>
      </header>

      {isBanned && (
        <div className="blocked-write-banner" role="status">
          Collaboration write actions are unavailable for this account. You can still review existing requests.
        </div>
      )}

      <div role="tablist" aria-label="Collaboration request lists" style={tabsStyle}>
        <TabButton active={tab === 'incoming'} count={pendingIncomingCount} onClick={() => setTab('incoming')}>
          Incoming
        </TabButton>
        <TabButton active={tab === 'sent'} count={pendingSentCount} onClick={() => setTab('sent')}>
          Sent
        </TabButton>
      </div>

      {loading && (
        <div aria-busy="true" style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
          <Spinner size={28} />
        </div>
      )}

      {!loading && error && (
        <div role="alert" style={alertStyle}>
          {error}
          <button onClick={fetchAll} style={inlineButtonStyle}>Retry</button>
        </div>
      )}

      {!loading && !error && tab === 'incoming' && (
        <RequestList
          type="incoming"
          items={incoming}
          isBanned={isBanned}
          pendingActionId={pendingActionId}
          onAction={handleAction}
        />
      )}

      {!loading && !error && tab === 'sent' && (
        <RequestList
          type="sent"
          items={sent}
          isBanned={isBanned}
          pendingActionId={pendingActionId}
          onAction={handleAction}
        />
      )}
    </div>
  )
}

function TabButton({ active, count, onClick, children }: {
  active: boolean
  count: number
  onClick: () => void
  children: string
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      style={{
        ...tabButtonStyle,
        borderBottomColor: active ? 'var(--color-brand)' : 'transparent',
        color: active ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
        fontWeight: active ? 700 : 600,
      }}
    >
      {children}
      {count > 0 && <span style={countBadgeStyle}>{count}</span>}
    </button>
  )
}

function RequestList({ type, items, isBanned, pendingActionId, onAction }: {
  type: Tab
  items: Collaboration[]
  isBanned: boolean
  pendingActionId: string | null
  onAction: (item: Collaboration, action: 'accept' | 'decline') => void
}) {
  if (items.length === 0) {
    return (
      <EmptyState
        icon={type === 'incoming' ? 'network' : 'upload'}
        title={type === 'incoming' ? 'No incoming requests' : 'No sent requests'}
        body={type === 'incoming'
          ? "When another builder requests to join one of your projects, it will appear here."
          : "Open a project from another builder and request collaboration from that project page."}
      />
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      {items.map(item => (
        <RequestCard
          key={item.id}
          item={item}
          type={type}
          isBanned={isBanned}
          isPendingAction={pendingActionId === item.id}
          onAction={onAction}
        />
      ))}
    </div>
  )
}

function RequestCard({ item, type, isBanned, isPendingAction, onAction }: {
  item: Collaboration
  type: Tab
  isBanned: boolean
  isPendingAction: boolean
  onAction: (item: Collaboration, action: 'accept' | 'decline') => void
}) {
  const actor = type === 'incoming' ? item.requester : item.owner
  const actorLabel = actor?.username ?? (type === 'incoming' ? item.requesterId : item.ownerId)
  const canDecide = type === 'incoming' && item.status === 'PENDING'
  const projectName = item.project?.name ?? item.projectId

  return (
    <article style={{ ...cardStyle, borderLeftColor: STATUS_COLORS[item.status] ?? 'var(--color-border)' }}>
      <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
        {actor?.avatarUrl ? (
          <img src={actor.avatarUrl} alt={`${actorLabel} avatar`} style={avatarStyle} />
        ) : (
          <div aria-hidden="true" style={avatarFallbackStyle}><Icon name="user" size={20} /></div>
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)', alignItems: 'flex-start', flexWrap: 'wrap' }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', alignItems: 'center' }}>
                <Link to={`/profile/${actor?.id ?? actorLabel}`} style={personLinkStyle}>
                  @{actorLabel}
                </Link>
                <span style={{ color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
                  {type === 'incoming' ? 'wants to collaborate on' : 'reviewing your request for'}
                </span>
              </div>
              <Link to={`/projects/${item.projectId}`} style={projectLinkStyle}>
                {projectName}
              </Link>
            </div>
            <StatusBadge status={item.status} />
          </div>

          {item.message && (
            <blockquote style={quoteStyle}>{item.message}</blockquote>
          )}

          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginTop: 'var(--space-3)', flexWrap: 'wrap' }}>
            {canDecide ? (
              <>
                <button
                  type="button"
                  disabled={isBanned || isPendingAction}
                  onClick={() => onAction(item, 'accept')}
                  style={actionButtonStyle(isBanned || isPendingAction, true)}
                >
                  {isPendingAction ? 'Saving...' : 'Accept'}
                </button>
                <button
                  type="button"
                  disabled={isBanned || isPendingAction}
                  onClick={() => onAction(item, 'decline')}
                  style={actionButtonStyle(isBanned || isPendingAction, false)}
                >
                  Decline
                </button>
              </>
            ) : (
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>
                {item.status === 'ACCEPTED' ? 'This request has been accepted.' : item.status === 'DECLINED' ? 'This request has been declined.' : 'Waiting for a decision.'}
              </span>
            )}
            <span style={timeStyle}>{timeAgo(item.resolvedAt ?? item.createdAt)}</span>
          </div>
        </div>
      </div>
    </article>
  )
}

function StatusBadge({ status }: { status: CollaborationStatus }) {
  return (
    <span style={{ ...statusBadgeStyle, color: STATUS_COLORS[status] }}>
      {STATUS_LABELS[status]}
    </span>
  )
}

function EmptyState({ icon, title, body }: { icon: Parameters<typeof Icon>[0]['name']; title: string; body: string }) {
  return (
    <div style={emptyStyle}>
      <div style={{ display: 'inline-flex', marginBottom: 'var(--space-4)' }}><Icon name={icon} size={44} /></div>
      <div style={{ fontSize: 'var(--text-base)', fontWeight: 700, color: 'var(--color-text-primary)', marginBottom: 'var(--space-2)' }}>{title}</div>
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', lineHeight: 1.5 }}>{body}</div>
    </div>
  )
}

const headerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-start',
  gap: 'var(--space-4)',
  marginBottom: 'var(--space-5)',
  flexWrap: 'wrap',
}

const eyebrowStyle: React.CSSProperties = {
  margin: '0 0 var(--space-2)',
  color: 'var(--color-brand-dim)',
  fontSize: 'var(--text-xs)',
  fontWeight: 800,
  letterSpacing: 0,
  fontFamily: 'var(--font-mono)',
}

const tabsStyle: React.CSSProperties = {
  display: 'flex',
  borderBottom: '1px solid var(--color-border)',
  marginBottom: 'var(--space-5)',
}

const tabButtonStyle: React.CSSProperties = {
  padding: '10px 20px',
  border: 'none',
  borderBottom: '2px solid transparent',
  background: 'none',
  fontSize: 'var(--text-sm)',
  cursor: 'pointer',
  fontFamily: 'inherit',
  display: 'flex',
  alignItems: 'center',
  gap: 6,
}

const countBadgeStyle: React.CSSProperties = {
  fontSize: 'var(--text-xs)',
  fontFamily: 'var(--font-mono)',
  background: 'var(--color-brand)',
  color: '#fff',
  borderRadius: 'var(--radius-full)',
  padding: '1px 6px',
}

const cardStyle: React.CSSProperties = {
  background: 'var(--color-surface)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-5)',
  border: '1px solid var(--color-border)',
  borderLeft: '3px solid var(--color-border)',
}

const avatarStyle: React.CSSProperties = {
  width: 44,
  height: 44,
  borderRadius: '50%',
  objectFit: 'cover',
  flexShrink: 0,
}

const avatarFallbackStyle: React.CSSProperties = {
  width: 44,
  height: 44,
  borderRadius: '50%',
  display: 'grid',
  placeItems: 'center',
  background: 'var(--color-surface-2)',
  color: 'var(--color-text-muted)',
  flexShrink: 0,
}

const personLinkStyle: React.CSSProperties = {
  fontWeight: 700,
  fontSize: 'var(--text-sm)',
  color: 'var(--color-text-primary)',
  textDecoration: 'none',
  overflowWrap: 'anywhere',
}

const projectLinkStyle: React.CSSProperties = {
  display: 'inline-block',
  marginTop: 4,
  fontWeight: 700,
  fontSize: 'var(--text-base)',
  color: 'var(--color-brand-dim)',
  textDecoration: 'none',
  overflowWrap: 'anywhere',
}

const quoteStyle: React.CSSProperties = {
  margin: 'var(--space-3) 0 0',
  padding: 'var(--space-3)',
  background: 'var(--color-surface-2)',
  borderRadius: 'var(--radius-md)',
  borderLeft: '2px solid var(--color-border)',
  fontSize: 'var(--text-sm)',
  color: 'var(--color-text-secondary)',
  lineHeight: 1.5,
}

const statusBadgeStyle: React.CSSProperties = {
  fontSize: 'var(--text-xs)',
  fontFamily: 'var(--font-mono)',
  fontWeight: 800,
  background: 'var(--color-surface-3)',
  padding: '3px 9px',
  borderRadius: 'var(--radius-full)',
  whiteSpace: 'nowrap',
}

const timeStyle: React.CSSProperties = {
  fontSize: 'var(--text-xs)',
  color: 'var(--color-text-muted)',
  fontFamily: 'var(--font-mono)',
  marginLeft: 'auto',
}

const alertStyle: React.CSSProperties = {
  padding: 'var(--space-4)',
  border: '1px solid var(--color-error)',
  borderRadius: 'var(--radius-lg)',
  color: 'var(--color-error)',
  background: 'var(--color-error-bg)',
  display: 'flex',
  justifyContent: 'space-between',
  gap: 'var(--space-3)',
  alignItems: 'center',
}

const inlineButtonStyle: React.CSSProperties = {
  border: '1px solid currentColor',
  background: 'transparent',
  borderRadius: 'var(--radius-md)',
  padding: '6px 12px',
  color: 'inherit',
  cursor: 'pointer',
}

const secondaryLinkStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '9px 16px',
  borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)',
  background: 'var(--color-surface)',
  color: 'var(--color-text-primary)',
  textDecoration: 'none',
  fontSize: 'var(--text-sm)',
  fontWeight: 700,
}

const emptyStyle: React.CSSProperties = {
  textAlign: 'center',
  padding: 'var(--space-12)',
  background: 'var(--color-surface)',
  borderRadius: 'var(--radius-lg)',
  border: '1px solid var(--color-border)',
}

function actionButtonStyle(disabled: boolean, primary: boolean): React.CSSProperties {
  return {
    padding: '8px 18px',
    borderRadius: 'var(--radius-md)',
    border: primary ? 'none' : '1px solid var(--color-border)',
    background: disabled ? 'var(--color-surface-2)' : primary ? 'var(--color-brand)' : 'transparent',
    color: disabled ? 'var(--color-text-muted)' : primary ? '#fff' : 'var(--color-text-secondary)',
    cursor: disabled ? 'not-allowed' : 'pointer',
    fontSize: 'var(--text-sm)',
    fontWeight: 700,
    fontFamily: 'inherit',
  }
}
