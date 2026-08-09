import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { authGitHub } from '../api/endpoints'
import { PageLoader } from '../components/common/Loading'
import { Icon } from '../components/Icon'
import { consumeAuthReturnTo } from '../auth/githubLogin'

function hasAdminRole(user: { roles?: string[] }) {
  return Boolean(user.roles?.some(role => role === 'ADMIN' || role === 'ROLE_ADMIN'))
}

function resolvePostLoginPath(user: { roles?: string[] }) {
  const returnTo = consumeAuthReturnTo()
  if (returnTo) return returnTo
  return hasAdminRole(user) ? '/admin' : '/feed'
}

export function AuthCallback() {
  const { login } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const ran = useRef(false)

  useEffect(() => {
    if (ran.current) return
    ran.current = true

    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const state = params.get('state')

    if (!code || !state) {
      setError('Missing OAuth parameters. Please try logging in again.')
      return
    }

    authGitHub(code, state)
      .then(async res => {
        const user = await login(res.data.accessToken, res.data.refreshToken)
        window.location.replace(resolvePostLoginPath(user))
      })
      .catch(err => {
        const msg = err.response?.data?.message
        if (err.response?.status === 401) {
          setError('OAuth state mismatch. Please restart the login flow.')
        } else {
          setError(msg || 'Authentication failed. Please try again.')
        }
      })
  }, [login])

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
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}>
            <Icon name="alert" size={28} />
          </div>
          <div style={{ fontWeight: 'var(--weight-semibold)', marginBottom: 'var(--space-2)' }}>
            Login failed
          </div>
          <div style={{ fontSize: 'var(--text-sm)', opacity: 0.85, marginBottom: 'var(--space-5)' }}>
            {error}
          </div>
          <a href="/" style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            background: 'var(--color-error)', color: '#fff',
            borderRadius: 'var(--radius-md)', padding: '8px 20px',
            fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)',
            textDecoration: 'none',
          }}>
            Back to home
          </a>
        </div>
      </div>
    )
  }

  return <PageLoader message="Connecting your GitHub identity..." />
}

const center: React.CSSProperties = {
  display: 'flex', flexDirection: 'column',
  alignItems: 'center', justifyContent: 'center',
  minHeight: '70vh', gap: 'var(--space-2)',
}
