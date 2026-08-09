import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  getIncomingCollabs, getSentCollabs,
  acceptCollaboration, declineCollaboration,
  requestCollaboration, getMyProjects,
} from '../api/endpoints'
import { Spinner } from '../components/Spinner'
import { Modal } from '../components/Modal'
import { toastSuccess, toastError } from '../components/Toast'
import { Icon } from '../components/Icon'

type Tab = 'incoming' | 'sent'

const STATUS_COLORS: Record<string, string> = {
  PENDING:  'var(--color-warning)',
  ACCEPTED: 'var(--color-success)',
  DECLINED: 'var(--color-text-muted)',
}

function timeAgo(iso: string) {
  const d = Date.now() - new Date(iso).getTime()
  const m = Math.floor(d / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

// ── Send Collaboration Request Modal ─────────────────────────────────────────
function SendRequestModal({ open, onClose, onSent }: {
  open: boolean; onClose: () => void; onSent: () => void
}) {
  const [projects, setProjects] = useState<any[]>([])
  const [projectId, setProjectId] = useState('')
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    getMyProjects()
      .then(r => {
        const list = Array.isArray(r.data) ? r.data : r.data.content ?? []
        setProjects(list)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [open])

  const handleSubmit = async () => {
    if (!projectId) return
    setSubmitting(true)
    try {
      await requestCollaboration(projectId, message.trim() || undefined)
      toastSuccess('Collaboration request sent')
      onSent()
      onClose()
      setProjectId('')
      setMessage('')
    } catch (err: any) {
      const msg = err?.response?.data?.message
      toastError(msg || 'Failed to send request')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Request to Collaborate" size="sm">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div>
          <label style={labelStyle}>Project *</label>
          {loading ? (
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>Loading projects…</div>
          ) : (
            <select value={projectId} onChange={e => setProjectId(e.target.value)} style={inputStyle}>
              <option value="">Select a project…</option>
              {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          )}
        </div>
        <div>
          <label style={labelStyle}>
            Message <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(optional)</span>
          </label>
          <textarea
            value={message}
            onChange={e => setMessage(e.target.value)}
            maxLength={500}
            rows={4}
            placeholder="Tell them why you'd like to collaborate…"
            style={{ ...inputStyle, resize: 'vertical' }}
          />
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', textAlign: 'right', marginTop: 4, fontFamily: 'var(--font-mono)' }}>
            {message.length}/500
          </div>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'flex-end' }}>
          <button onClick={onClose} style={ghostBtn}>Cancel</button>
          <button
            onClick={handleSubmit}
            disabled={!projectId || submitting}
            style={{ ...primaryBtn, opacity: !projectId || submitting ? 0.5 : 1, cursor: !projectId || submitting ? 'not-allowed' : 'pointer' }}
          >
            {submitting ? 'Sending…' : 'Send Request'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export function CollaborationsPage() {
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('incoming')
  const [incoming, setIncoming] = useState<any[]>([])
  const [sent, setSent] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [showSendModal, setShowSendModal] = useState(false)
  const isBanned = Boolean(user?.banned)

  const fetchAll = useCallback(async () => {
    setLoading(true)
    try {
      const [inRes, sentRes] = await Promise.allSettled([
        getIncomingCollabs(),
        getSentCollabs(),
      ])
      if (inRes.status === 'fulfilled') {
        setIncoming(inRes.value.data.content ?? inRes.value.data ?? [])
      }
      if (sentRes.status === 'fulfilled') {
        setSent(sentRes.value.data.content ?? sentRes.value.data ?? [])
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])

  const handleAction = async (id: string, action: 'accept' | 'decline') => {
    if (isBanned) {
      toastError('Writes are blocked for this account.')
      return
    }
    try {
      if (action === 'accept') {
        await acceptCollaboration(id)
        toastSuccess('Collaboration accepted')
      } else {
        await declineCollaboration(id)
        toastSuccess('Request declined')
      }
      setIncoming(prev => prev.filter(c => c.id !== id))
    } catch {
      toastError('Action failed. Try again.')
    }
  }

  const pendingIncoming = incoming.filter(c => c.status === 'PENDING')

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-5)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700 }}>Collaborations</h1>
        <button
          disabled={isBanned}
          onClick={() => !isBanned && setShowSendModal(true)}
          style={{ ...primaryBtn, opacity: isBanned ? 0.55 : 1, cursor: isBanned ? 'not-allowed' : 'pointer' }}
        >
          {isBanned ? 'Writes blocked' : '+ Send Request'}
        </button>
      </div>

      {isBanned && (
        <div className="blocked-write-banner" role="status">
          Collaboration write actions are unavailable for this account. You can still review existing requests.
        </div>
      )}

      {/* Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--space-5)' }}>
        {([
          { key: 'incoming', label: 'Incoming', count: pendingIncoming.length },
          { key: 'sent',     label: 'Sent',     count: sent.length },
        ] as const).map(t => (
          <button key={t.key} onClick={() => setTab(t.key)} style={{
            padding: '10px 20px', border: 'none', background: 'none',
            borderBottom: tab === t.key ? '2px solid var(--color-brand)' : '2px solid transparent',
            color: tab === t.key ? 'var(--color-text-primary)' : 'var(--color-text-muted)',
            fontSize: 'var(--text-sm)', fontWeight: tab === t.key ? 600 : 500,
            cursor: 'pointer', fontFamily: 'inherit',
            display: 'flex', alignItems: 'center', gap: 6,
            transition: 'all var(--transition-fast)',
          }}>
            {t.label}
            {t.count > 0 && (
              <span style={{
                fontSize: 'var(--text-xs)', fontFamily: 'var(--font-mono)',
                background: tab === t.key ? 'var(--color-brand)' : 'var(--color-surface-3)',
                color: tab === t.key ? '#fff' : 'var(--color-text-muted)',
                borderRadius: 'var(--radius-full)', padding: '1px 6px',
              }}>{t.count}</span>
            )}
          </button>
        ))}
      </div>

      {loading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
          <Spinner size={28} />
        </div>
      )}

      {/* Incoming tab */}
      {!loading && tab === 'incoming' && (
        <>
          {incoming.length === 0 ? (
            <EmptyState icon="network" title="No incoming requests" body="When someone wants to collaborate on your projects, you'll see them here." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              {incoming.map(c => (
                <div key={c.id} style={{
                  background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
                  padding: 'var(--space-5)',
                  borderLeft: `3px solid ${STATUS_COLORS[c.status] ?? 'var(--color-border)'}`,
                }}>
                  <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                    {c.requester?.avatarUrl && (
                      <img src={c.requester.avatarUrl} alt="" style={{ width: 44, height: 44, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
                    )}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap', marginBottom: 'var(--space-1)' }}>
                        <Link to={`/profile/${c.requester?.username}`}
                          style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-text-primary)' }}>
                          @{c.requester?.username ?? c.requesterId}
                        </Link>
                        <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>wants to collaborate on</span>
                        <Link to={`/projects/${c.projectId}`}
                          style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-brand-dim)' }}>
                          {c.project?.name ?? c.projectId}
                        </Link>
                      </div>
                      {c.message && (
                        <blockquote style={{
                          margin: 'var(--space-2) 0',
                          padding: 'var(--space-3)',
                          background: 'var(--color-surface-2)',
                          borderRadius: 'var(--radius-md)',
                          borderLeft: '2px solid var(--color-border)',
                          fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)',
                          fontStyle: 'italic',
                        }}>
                          "{c.message}"
                        </blockquote>
                      )}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginTop: 'var(--space-3)' }}>
                        {c.status === 'PENDING' ? (
                          <>
                            <button disabled={isBanned} onClick={() => handleAction(c.id, 'accept')} style={{ ...primaryBtn, opacity: isBanned ? 0.55 : 1, cursor: isBanned ? 'not-allowed' : 'pointer' }}>Accept</button>
                            <button disabled={isBanned} onClick={() => handleAction(c.id, 'decline')} style={{ ...ghostBtn, opacity: isBanned ? 0.55 : 1, cursor: isBanned ? 'not-allowed' : 'pointer' }}>Decline</button>
                          </>
                        ) : (
                          <span style={{
                            fontSize: 'var(--text-xs)', fontFamily: 'var(--font-mono)',
                            color: STATUS_COLORS[c.status], fontWeight: 600,
                          }}>{c.status}</span>
                        )}
                        <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)', marginLeft: 'auto' }}>
                          {timeAgo(c.createdAt)}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* Sent tab */}
      {!loading && tab === 'sent' && (
        <>
          {sent.length === 0 ? (
            <EmptyState icon="upload" title="No sent requests" body="Send a collaboration request from any project page." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              {sent.map(c => (
                <div key={c.id} style={{
                  background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
                  padding: 'var(--space-5)',
                  borderLeft: `3px solid ${STATUS_COLORS[c.status] ?? 'var(--color-border)'}`,
                }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 'var(--space-3)' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap', marginBottom: 'var(--space-1)' }}>
                        <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>Request to collaborate on</span>
                        <Link to={`/projects/${c.projectId}`}
                          style={{ fontWeight: 600, fontSize: 'var(--text-sm)', color: 'var(--color-brand-dim)' }}>
                          {c.project?.name ?? c.projectId}
                        </Link>
                      </div>
                      {c.message && (
                        <blockquote style={{
                          margin: 'var(--space-2) 0 0',
                          padding: 'var(--space-3)',
                          background: 'var(--color-surface-2)',
                          borderRadius: 'var(--radius-md)',
                          borderLeft: '2px solid var(--color-border)',
                          fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)',
                          fontStyle: 'italic',
                        }}>
                          "{c.message}"
                        </blockquote>
                      )}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                      <span style={{
                        fontSize: 'var(--text-xs)', fontFamily: 'var(--font-mono)',
                        fontWeight: 600, color: STATUS_COLORS[c.status] ?? 'var(--color-text-muted)',
                        background: 'var(--color-surface-3)',
                        padding: '2px 8px', borderRadius: 'var(--radius-full)',
                      }}>{c.status}</span>
                      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-mono)' }}>
                        {timeAgo(c.createdAt)}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      <SendRequestModal
        open={showSendModal}
        onClose={() => setShowSendModal(false)}
        onSent={fetchAll}
      />
    </div>
  )
}

function EmptyState({ icon, title, body }: { icon: Parameters<typeof Icon>[0]['name']; title: string; body: string }) {
  return (
    <div style={{
      textAlign: 'center', padding: 'var(--space-12)',
      background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
    }}>
      <div style={{ display: 'inline-flex', marginBottom: 'var(--space-4)' }}><Icon name={icon} size={44} /></div>
      <div style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: 'var(--space-2)' }}>{title}</div>
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>{body}</div>
    </div>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: 'var(--text-sm)', fontWeight: 600,
  color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)',
}
const inputStyle: React.CSSProperties = {
  width: '100%', padding: '9px 12px',
  borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)',
  background: 'var(--color-surface-2)',
  fontSize: 'var(--text-sm)', fontFamily: 'inherit',
  color: 'var(--color-text-primary)', outline: 'none',
}
const ghostBtn: React.CSSProperties = {
  padding: '8px 18px', borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)', background: 'transparent',
  color: 'var(--color-text-secondary)', cursor: 'pointer',
  fontSize: 'var(--text-sm)', fontWeight: 500, fontFamily: 'inherit',
}
const primaryBtn: React.CSSProperties = {
  padding: '8px 18px', borderRadius: 'var(--radius-md)', border: 'none',
  background: 'linear-gradient(135deg, var(--color-brand), var(--color-brand-dim))',
  color: '#fff', cursor: 'pointer', fontSize: 'var(--text-sm)',
  fontWeight: 600, fontFamily: 'inherit',
}
