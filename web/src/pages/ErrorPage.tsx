import { useParams, Link } from 'react-router-dom'

const ERROR_CONFIG: Record<string, { icon: string; title: string; message: string }> = {
  '404': { icon: '🔭', title: 'Page not found', message: "This page doesn't exist or was moved." },
  '429': { icon: '⏱', title: 'Too many requests', message: 'Slow down — you\'re being rate limited. Try again in a moment.' },
  '503': { icon: '⚡', title: 'Service unavailable', message: 'The server is under high load. Hang tight and retry shortly.' },
  'default': { icon: '💥', title: 'Something went wrong', message: 'An unexpected error occurred.' },
}

export function ErrorPage() {
  const { code } = useParams<{ code: string }>()
  const cfg = ERROR_CONFIG[code ?? 'default'] ?? ERROR_CONFIG.default

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', minHeight: '100vh',
      background: 'var(--color-bg)', textAlign: 'center', padding: 'var(--space-6)',
    }}>
      <div style={{ fontSize: 56, marginBottom: 'var(--space-5)' }}>{cfg.icon}</div>
      {code && (
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginBottom: 'var(--space-2)' }}>
          HTTP {code}
        </div>
      )}
      <h1 style={{ fontSize: 'var(--text-2xl)', fontWeight: 700, marginBottom: 'var(--space-3)', color: 'var(--color-text-primary)' }}>
        {cfg.title}
      </h1>
      <p style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-muted)', maxWidth: 400, marginBottom: 'var(--space-8)' }}>
        {cfg.message}
      </p>
      <Link to="/" style={{
        padding: '10px 24px', borderRadius: 'var(--radius-md)', border: 'none',
        background: 'linear-gradient(135deg, var(--color-brand), var(--color-brand-dim))',
        color: '#fff', fontWeight: 600, fontSize: 'var(--text-sm)',
      }}>← Go Home</Link>
    </div>
  )
}
