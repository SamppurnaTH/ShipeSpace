import { useState } from 'react'
import { getOAuthState } from '../api/endpoints'

export function AuthPage() {
  const [loading, setLoading] = useState(false)

  const handleLogin = async () => {
    setLoading(true)
    try {
      const res = await getOAuthState()
      const state = res.data.state
      const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID
      const redirectUri = import.meta.env.VITE_GITHUB_REDIRECT_URI
        || `${window.location.origin}/auth/callback`
      window.location.href =
        `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${state}&scope=read:user,user:email`
    } catch {
      alert('Could not reach the server. Is the backend running?')
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: '2rem',
    }}>
      <div style={{
        background: 'rgba(255,255,255,0.1)',
        backdropFilter: 'blur(10px)',
        border: '1px solid rgba(255,255,255,0.2)',
        borderRadius: 16,
        padding: '3rem',
        textAlign: 'center',
        maxWidth: 400,
        width: '100%',
        boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
      }}>
        <div style={{ fontSize: 48, marginBottom: '1rem' }}>⚓</div>
        <h1 style={{
          fontSize: '2rem',
          fontWeight: 700,
          color: '#fff',
          marginBottom: '1rem',
          textShadow: '0 2px 10px rgba(0,0,0,0.3)',
        }}>
          Welcome to ShipSpace
        </h1>
        <p style={{
          color: 'rgba(255,255,255,0.9)',
          marginBottom: '2rem',
          textShadow: '0 1px 5px rgba(0,0,0,0.2)',
        }}>
          Sign in with GitHub to access your personalized feed and start shipping.
        </p>
        <button
          onClick={handleLogin}
          disabled={loading}
          style={{
            padding: '12px 24px',
            borderRadius: 8,
            fontSize: 16,
            fontWeight: 600,
            background: '#fff',
            color: '#667eea',
            border: 'none',
            cursor: loading ? 'not-allowed' : 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            transition: 'all 200ms ease',
            boxShadow: '0 4px 15px rgba(0,0,0,0.1)',
          }}
          onMouseEnter={e => !loading && (e.currentTarget.style.transform = 'translateY(-2px)')}
          onMouseLeave={e => !loading && (e.currentTarget.style.transform = 'none')}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/>
          </svg>
          {loading ? 'Connecting...' : 'Continue with GitHub'}
        </button>
      </div>
    </div>
  )
}