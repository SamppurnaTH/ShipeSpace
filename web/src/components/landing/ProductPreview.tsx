const feedItems = [
  { user: 'kaito_builds', avatar: 'KB', project: 'Collab.rs', tags: ['Rust', 'WebAssembly'], content: 'Shipped the CRDT merge algorithm. <2ms merge time for documents up to 100k chars. No central server needed for conflict resolution.', likes: 24, comments: 7, time: '2h ago' },
  { user: 'alex_launches', avatar: 'AL', project: 'LaunchPad', tags: ['Next.js', 'SaaS'], content: 'LaunchPad hit 200 users without any marketing. Shipped dark mode and custom domain support this week.', likes: 41, comments: 12, time: '5h ago' },
]

const debugEntries = [
  { status: 200, url: '/feeds/personalized', ms: 87, mode: 'personalized', rl: '118' },
  { status: 200, url: '/ships/69d1/like', ms: 23, mode: null, rl: '117' },
  { status: 200, url: '/feeds/personalized', ms: 91, mode: 'personalized', rl: '116' },
  { status: 429, url: '/feeds/personalized', ms: 4, mode: null, rl: '0' },
]

export function ProductPreview() {
  return (
    <section style={{
      padding: 'clamp(60px, 8vw, 100px) clamp(16px, 4vw, 64px)',
      background: '#fff', borderBottom: '1px solid #e5e7eb',
    }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ marginBottom: 56 }}>
          <p style={{ fontSize: 13, fontWeight: 600, color: '#1d4ed8', letterSpacing: '0.06em', textTransform: 'uppercase', margin: '0 0 10px' }}>
            Product preview
          </p>
          <h2 style={{ fontSize: 'clamp(26px, 3.5vw, 40px)', fontWeight: 800, color: '#111827', letterSpacing: '-0.02em', lineHeight: 1.2, margin: '0 0 14px', maxWidth: 560 }}>
            What it looks like in practice
          </h2>
          <p style={{ fontSize: 16, color: '#6b7280', lineHeight: 1.6, margin: 0, maxWidth: 480 }}>
            The feed, the debug panel, and the system state — all visible at once.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20 }}>
          {/* Feed preview */}
          <div style={{ border: '1px solid #e5e7eb', borderRadius: 12, overflow: 'hidden', background: '#fff', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}>
            <div style={{ padding: '10px 16px', borderBottom: '1px solid #e5e7eb', background: '#f9fafb', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>⚓ ShipSpace</span>
              <span style={{ marginLeft: 'auto', fontSize: 11, fontWeight: 600, padding: '2px 8px', borderRadius: 4, background: '#eff6ff', color: '#1d4ed8', border: '1px solid #bfdbfe' }}>
                ✦ Personalized
              </span>
            </div>
            <div style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {feedItems.map(item => (
                <div key={item.user} style={{ border: '1px solid #e5e7eb', borderRadius: 8, padding: '14px' }}>
                  <div style={{ display: 'flex', gap: 10, marginBottom: 10, alignItems: 'center' }}>
                    <div style={{ width: 32, height: 32, borderRadius: '50%', flexShrink: 0, background: '#1d4ed8', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, color: '#fff' }}>
                      {item.avatar}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 12, fontWeight: 600, color: '#111827' }}>{item.user}</div>
                      <div style={{ display: 'flex', gap: 4, marginTop: 2, flexWrap: 'wrap' }}>
                        <span style={{ fontSize: 10, color: '#6b7280' }}>{item.project}</span>
                        {item.tags.map(t => (
                          <span key={t} style={{ fontSize: 9, padding: '1px 5px', borderRadius: 3, background: '#eff6ff', color: '#1d4ed8' }}>{t}</span>
                        ))}
                      </div>
                    </div>
                    <span style={{ fontSize: 10, color: '#9ca3af', flexShrink: 0 }}>{item.time}</span>
                  </div>
                  <p style={{ fontSize: 12, color: '#374151', lineHeight: 1.6, margin: '0 0 10px' }}>{item.content}</p>
                  <div style={{ display: 'flex', gap: 14, borderTop: '1px solid #f3f4f6', paddingTop: 8 }}>
                    <span style={{ fontSize: 11, color: '#9ca3af' }}>♡ {item.likes}</span>
                    <span style={{ fontSize: 11, color: '#9ca3af' }}>💬 {item.comments}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Debug panel preview */}
          <div style={{ border: '1px solid #e5e7eb', borderRadius: 12, overflow: 'hidden', background: '#1e293b', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}>
            <div style={{ padding: '10px 16px', borderBottom: '1px solid #334155', background: '#0f172a', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8', fontFamily: "'JetBrains Mono', monospace" }}>⚙ DEBUG</span>
              <span style={{ fontSize: 10, color: '#475569', fontFamily: "'JetBrains Mono', monospace" }}>(4)</span>
              <div style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
                {['all', 'errors', 'slow'].map(f => (
                  <span key={f} style={{ fontSize: 9, padding: '2px 6px', borderRadius: 3, background: f === 'all' ? '#1e293b' : 'none', border: `1px solid ${f === 'all' ? '#334155' : 'transparent'}`, color: f === 'all' ? '#e2e8f0' : '#475569', fontFamily: "'JetBrains Mono', monospace" }}>{f}</span>
                ))}
              </div>
            </div>
            <div style={{ padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8, fontFamily: "'JetBrains Mono', monospace" }}>
              {debugEntries.map((e, i) => (
                <div key={i} style={{ borderLeft: `3px solid ${e.status === 429 ? '#facc15' : e.status >= 400 ? '#f87171' : '#4ade80'}`, paddingLeft: 8, background: e.status >= 400 ? 'rgba(239,68,68,0.05)' : 'transparent', borderRadius: '0 4px 4px 0', paddingTop: 2, paddingBottom: 2 }}>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}>
                    <span style={{ fontSize: 11, fontWeight: 700, minWidth: 30, color: e.status === 429 ? '#facc15' : e.status >= 400 ? '#f87171' : '#4ade80' }}>{e.status}</span>
                    <span style={{ fontSize: 10, color: '#475569', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.url}</span>
                    <span style={{ fontSize: 10, color: '#475569', flexShrink: 0 }}>{e.ms}ms</span>
                  </div>
                  {e.mode && <div style={{ fontSize: 10, color: '#6366f1', marginTop: 1 }}>mode: <strong>{e.mode}</strong></div>}
                  {e.rl && <div style={{ fontSize: 10, color: '#475569', marginTop: 1 }}>rl: <strong style={{ color: Number(e.rl) < 10 ? '#f59e0b' : '#64748b' }}>{e.rl}</strong></div>}
                </div>
              ))}
            </div>
            <div style={{ padding: '10px 16px', borderTop: '1px solid #1e293b', fontSize: 11, color: '#475569', fontFamily: "'JetBrains Mono', monospace" }}>
              The 429 is real — rate limiting is active and visible.
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
