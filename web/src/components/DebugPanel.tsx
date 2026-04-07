import { useEffect, useRef, useState } from 'react'
import { onRequest } from '../api/client'
import type { RequestMeta } from '../api/client'

const MAX_ENTRIES = 50
type Filter = 'all' | 'errors' | 'slow'

export function DebugPanel() {
  const [open, setOpen] = useState(false)
  const [entries, setEntries] = useState<RequestMeta[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  const unsubRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    const handler = (meta: RequestMeta) =>
      setEntries(prev => [meta, ...prev].slice(0, MAX_ENTRIES))
    unsubRef.current = onRequest(handler)
    return () => { unsubRef.current?.() }
  }, [])

  const filtered = entries.filter(e => {
    if (filter === 'errors') return e.status >= 400 || e.status === 0
    if (filter === 'slow')   return e.latencyMs > 500
    return true
  })
  const errorCount = entries.filter(e => e.status >= 400 || e.status === 0).length

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} title="Open debug panel" style={{
        position: 'fixed', bottom: 16, right: 16,
        background: errorCount > 0 ? 'var(--color-error)' : '#1e293b',
        color: '#fff', border: 'none', borderRadius: 8,
        padding: '6px 12px', cursor: 'pointer', fontSize: 16,
        zIndex: 9999, display: 'flex', alignItems: 'center', gap: 6,
        boxShadow: 'var(--shadow-md)', fontFamily: 'var(--font-mono)',
      }}>
        ⚙
        {errorCount > 0 && (
          <span style={{
            background: '#fff', color: 'var(--color-error)',
            borderRadius: 999, fontSize: 10, fontWeight: 700, padding: '1px 5px',
          }}>{errorCount}</span>
        )}
      </button>
    )
  }

  return (
    <div style={{
      position: 'fixed', bottom: 0, right: 0, width: 400, maxHeight: '65vh',
      background: '#0f172a', color: '#e2e8f0',
      borderTop: '1px solid #1e293b', borderLeft: '1px solid #1e293b',
      borderRadius: '10px 0 0 0', display: 'flex', flexDirection: 'column',
      fontFamily: 'var(--font-mono)', fontSize: 11, zIndex: 9999,
      boxShadow: 'var(--shadow-lg)',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 6,
        padding: '8px 12px', borderBottom: '1px solid #1e293b', background: '#0a0f1e',
      }}>
        <span style={{ fontWeight: 700, fontSize: 12, color: '#94a3b8', flex: 1 }}>
          ⚙ DEBUG {entries.length > 0 && <span style={{ color: '#475569' }}>({entries.length})</span>}
        </span>
        {(['all', 'errors', 'slow'] as Filter[]).map(f => (
          <button key={f} onClick={() => setFilter(f)} style={{
            background: filter === f ? '#1e293b' : 'none',
            border: `1px solid ${filter === f ? '#334155' : 'transparent'}`,
            color: filter === f ? '#e2e8f0' : '#475569',
            borderRadius: 4, padding: '2px 7px', cursor: 'pointer',
            fontSize: 10, fontFamily: 'inherit',
          }}>
            {f}{f === 'errors' && errorCount > 0 ? ` (${errorCount})` : ''}
          </button>
        ))}
        <button onClick={() => setEntries([])} style={{
          background: 'none', border: 'none', color: '#475569',
          cursor: 'pointer', fontSize: 11, padding: '2px 4px', fontFamily: 'inherit',
        }}>CLR</button>
        <button onClick={() => setOpen(false)} style={{
          background: 'none', border: 'none', color: '#475569',
          cursor: 'pointer', fontSize: 14, padding: '2px 4px',
        }}>✕</button>
      </div>

      <div style={{ overflowY: 'auto', flex: 1, padding: 8 }}>
        {filtered.length === 0 && (
          <div style={{ color: '#334155', padding: '12px 8px', textAlign: 'center' }}>
            {entries.length === 0 ? 'No requests yet' : 'No matches'}
          </div>
        )}
        {filtered.map((e, i) => (
          <div key={i} style={{
            borderLeft: `3px solid ${statusColor(e.status)}`,
            paddingLeft: 8, marginBottom: 8,
            background: e.status >= 400 ? 'rgba(239,68,68,.05)' : 'transparent',
            borderRadius: '0 4px 4px 0',
          }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}>
              <span style={{ fontWeight: 700, minWidth: 30, color: statusColor(e.status) }}>
                {e.status || 'ERR'}
              </span>
              <span style={{
                color: '#64748b', flex: 1,
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }} title={e.url}>{e.url}</span>
              <span style={{
                color: e.latencyMs > 500 ? 'var(--color-warning)' : '#475569',
                minWidth: 48, textAlign: 'right', flexShrink: 0,
              }}>{e.latencyMs}ms</span>
            </div>
            {e.feedMode && (
              <div style={{ color: '#6366f1', marginTop: 2 }}>
                mode: <strong>{e.feedMode}</strong>
              </div>
            )}
            {e.rateLimitRemaining && (
              <div style={{ color: '#64748b', marginTop: 2 }}>
                rl: <strong style={{ color: Number(e.rateLimitRemaining) < 10 ? 'var(--color-warning)' : '#94a3b8' }}>
                  {e.rateLimitRemaining}
                </strong>
              </div>
            )}
            {e.error && (
              <div style={{ color: '#f87171', marginTop: 2, wordBreak: 'break-word' }}>
                {e.error}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

const statusColor = (s: number) => {
  if (s === 0 || s >= 500) return '#f87171'
  if (s === 429)           return '#facc15'
  if (s >= 400)            return '#fb923c'
  return '#4ade80'
}
