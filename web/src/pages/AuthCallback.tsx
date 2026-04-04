import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { authGitHub } from '../api/endpoints'

export function AuthCallback() {
  const { login } = useAuth()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const code  = params.get('code')
    const state = params.get('state')

    if (!code || !state) {
      setError('Missing OAuth code or state. Please try logging in again.')
      return
    }

    authGitHub(code, state)
      .then(res => {
        login(res.data.accessToken, res.data.refreshToken)
        window.location.href = '/'
      })
      .catch(err => {
        setError(err.response?.data?.message || 'Authentication failed. Please try again.')
      })
  }, [])

  if (error) {
    return (
      <div style={styles.center}>
        <div style={styles.error}>
          <div style={{ fontSize: 24, marginBottom: 8 }}>⚠</div>
          <div>{error}</div>
          <a href="/" style={styles.link}>← Back to home</a>
        </div>
      </div>
    )
  }

  return (
    <div style={styles.center}>
      <div style={styles.spinner} />
      <div style={{ color: '#64748b', marginTop: 12 }}>Completing login…</div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  center: {
    display: 'flex', flexDirection: 'column',
    alignItems: 'center', justifyContent: 'center',
    minHeight: '60vh', gap: 8,
  },
  error: {
    background: '#fef2f2', border: '1px solid #fecaca',
    borderRadius: 10, padding: 24, textAlign: 'center',
    color: '#dc2626', maxWidth: 360,
  },
  link: { display: 'block', marginTop: 12, color: '#3b82f6', fontSize: 13 },
  spinner: {
    width: 32, height: 32, border: '3px solid #e2e8f0',
    borderTop: '3px solid #3b82f6', borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
  },
}
