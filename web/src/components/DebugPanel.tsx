import { useEffect, useRef, useState } from 'react'
import { onRequest } from '../api/client'
import type { RequestMeta } from '../api/client'

const MAX_ENTRIES = 20

export function DebugPanel() {
  const [open, setOpen] = useState(false)
  const [entries, setEntries] = useState<RequestMeta[]>([])
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    onRequest(meta => {
      setEntries(prev => [meta, ...prev].slice(0, MAX_ENTRIES))
    })
  }, [])

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        style={styles.toggle}
        title="Open debug panel"
      >
        🛠
      </button>
    )
  }

  return (
    <div style={styles.panel}>
      <div style={styles.header}>
        <span style={{ fontWeight: 'bold' }}>Debug Panel</span>
        <button onClick={() => setOpen(false)} style={styles.close}>✕</button>
      </div>
      <div ref={listRef} style={styles.list}>
        {entries.length === 0 && (
          <div style={styles.empty}>No requests yet</div>
        )}
        {entries.map((e, i) => (
          <div key={i} style={{ ...styles.entry, borderColor: statusColor(e.status) }}>
            <div style={styles.row}>
              <span style={{ ...styles.status, color: statusColor(e.status) }}>
                {e.status || 'ERR'}
              </span>
              <span style={styles.url}>{e.url}</span>
              <span style={styles.latency}>{e.latencyMs}ms</span>
            </div>
            {e.feedMode && (
              <div style={styles.badge}>
                feed: <strong>{e.feedMode}</strong>
              </div>
            )}
            {e.rateLimitRemaining && (
              <div style={styles.badge}>
                rate-limit remaining: <strong>{e.rateLimitRemaining}</strong>
              </div>
            )}
            {e.error && (
              <div style={{ ...styles.badge, color: '#f87171' }}>
                {e.error}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

const statusColor = (status: number) => {
  if (status >= 500) return '#f87171'
  if (status === 503) return '#fb923c'
  if (status === 429) return '#facc15'
  if (status >= 400) return '#fb923c'
  return '#4ade80'
}

const styles: Record<string, React.CSSProperties> = {
  toggle: {
    position: 'fixed', bottom: 16, right: 16,
    background: '#1e293b', color: '#fff',
    border: 'none', borderRadius: 8, padding: '8px 12px',
    cursor: 'pointer', fontSize: 18, zIndex: 9999,
  },
  panel: {
    position: 'fixed', bottom: 0, right: 0,
    width: 380, maxHeight: '60vh',
    background: '#0f172a', color: '#e2e8f0',
    borderTop: '1px solid #334155', borderLeft: '1px solid #334155',
    borderRadius: '8px 0 0 0',
    display: 'flex', flexDirection: 'column',
    fontFamily: 'monospace', fontSize: 12, zIndex: 9999,
  },
  header: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '8px 12px', borderBottom: '1px solid #334155',
    background: '#1e293b',
  },
  close: {
    background: 'none', border: 'none', color: '#94a3b8',
    cursor: 'pointer', fontSize: 14,
  },
  list: { overflowY: 'auto', flex: 1, padding: 8 },
  empty: { color: '#64748b', padding: 8 },
  entry: {
    borderLeft: '3px solid', paddingLeft: 8, marginBottom: 8,
  },
  row: { display: 'flex', gap: 8, alignItems: 'baseline' },
  status: { fontWeight: 'bold', minWidth: 32 },
  url: { color: '#94a3b8', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  latency: { color: '#64748b', minWidth: 48, textAlign: 'right' },
  badge: { color: '#94a3b8', marginTop: 2 },
}
