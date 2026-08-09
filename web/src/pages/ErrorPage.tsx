import { useParams, Link } from 'react-router-dom'
import { Icon } from '../components/Icon'

type ErrorIcon = Parameters<typeof Icon>[0]['name']

const ERROR_CONFIG: Record<string, { icon: ErrorIcon; title: string; message: string }> = {
  '404': { icon: 'search', title: 'Page not found', message: "This page doesn't exist or was moved." },
  '429': { icon: 'alert', title: 'Too many requests', message: "You're being rate limited. Try again in a moment." },
  '503': { icon: 'alert', title: 'Service unavailable', message: 'The server is under high load. Retry shortly.' },
  default: { icon: 'alert', title: 'Something went wrong', message: 'An unexpected error occurred.' },
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
      <div style={{ display: 'inline-flex', marginBottom: 'var(--space-5)', color: 'var(--color-brand)' }}>
        <Icon name={cfg.icon} size={56} />
      </div>
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
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        padding: '10px 24px', borderRadius: 'var(--radius-md)', border: 'none',
        background: 'linear-gradient(135deg, var(--color-brand), var(--color-brand-dim))',
        color: '#fff', fontWeight: 600, fontSize: 'var(--text-sm)', textDecoration: 'none',
      }}>Go home</Link>
    </div>
  )
}
