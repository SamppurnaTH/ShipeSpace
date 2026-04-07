import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { authGitHub } from '../api/endpoints'
import { Spinner } from '../components/Spinner'

export function AuthCallback() {
  const { login } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const ran = useRef(false) // prevent double-fire in StrictMode

  useEffect(() => {
    if (ran.current) return
    ran.current = true

    const params = new URLSearchParams(window.location.search)
    const code  = params.get('code')
    const state = params.get('state')

    if (!code || !state) {
      setError('Missing OAuth parameters. Please try logging in again.')
      return
    }

    authGitHub(code, state)
      .then(async res => {
        await login(res.data.accessToken, res.data.refreshToken)
        window.location.replace('/')
      })
      .catch(err => {
        const msg = err.response?.data?.message
        if (err.response?.status === 401) {
          setError('OAuth state mismatch — possible CSRF. Please try again.')
        } else {
          setError(msg || 'Authentication failed. Please try again.')
        }
      })
  }, [])

  if (error) {
    return (
      <div style={center}>
        <div style={{
          background: 'var(--color-error-bg)',
          border: '1px solid var(--color-error-border)',
          borderRadius: 'var(--radius-lg)', padding: 'var(--space-8)',
          textAlign: 'center', color: 'var(--color-error)', maxWidth: 380,
          animation: 'fadeIn 200ms ease',
        }}>
          <div style={{ fontSize: 28, marginBottom: 'var(--space-3)' }}>⚠</div>
          <div style={{ fontWeight: 'var(--weight-semibold)', marginBottom: 'var(--space-2)' }}>
            Login failed
          </div>
          <div style={{ fontSize: 'var(--text-sm)', opacity: 0.85, marginBottom: 'var(--space-5)' }}>
            {error}
          </div>
          <a href="/" style={{
            display: 'inline-block',
            background: 'var(--color-error)', color: '#fff',
            borderRadius: 'var(--radius-md)', padding: '8px 20px',
            fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)',
          }}>
            ← Back to home
          </a>
        </div>
      </div>
    )
  }

  return (
    <div style={center}>
      <Spinner size={36} />
      <div style={{ color: 'var(--color-text-muted)', marginTop: 'var(--space-4)', fontSize: 'var(--text-sm)' }}>
        Completing login…
      </div>
    </div>
  )
}

const center: React.CSSProperties = {
  display: 'flex', flexDirection: 'column',
  alignItems: 'center', justifyContent: 'center',
  minHeight: '70vh', gap: 'var(--space-2)',
}
