import { AuthProvider, useAuth } from './auth/AuthContext'
import { FeedPage } from './pages/FeedPage'
import { AuthCallback } from './pages/AuthCallback'
import { DebugPanel } from './components/DebugPanel'
import { getOAuthState, logout as apiLogout } from './api/endpoints'

function Header() {
  const { user, logout } = useAuth()

  const handleLogin = async () => {
    try {
      const res = await getOAuthState()
      const state = res.data.state
      const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID
      const redirectUri = import.meta.env.VITE_GITHUB_REDIRECT_URI || `${window.location.origin}/auth/callback`
      window.location.href =
        `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${state}&scope=read:user,user:email`
    } catch {
      alert('Failed to start login. Is the backend running?')
    }
  }

  const handleLogout = async () => {
    const refreshToken = localStorage.getItem('refresh_token') || undefined
    try { await apiLogout(refreshToken) } catch { /* best effort */ }
    logout()
  }

  return (
    <header style={styles.header}>
      <a href="/" style={styles.logo}>⚓ ShipSpace</a>
      <div style={styles.nav}>
        {user ? (
          <>
            <img src={user.avatarUrl} alt="" style={styles.avatar} />
            <span style={styles.username}>{user.username}</span>
            <span style={styles.rank}>{user.rank} · L{user.level}</span>
            <button onClick={handleLogout} style={styles.btn}>Logout</button>
          </>
        ) : (
          <button onClick={handleLogin} style={{ ...styles.btn, ...styles.btnPrimary }}>
            Login with GitHub
          </button>
        )}
      </div>
    </header>
  )
}

function Router() {
  const path = window.location.pathname
  if (path === '/auth/callback') return <AuthCallback />
  return <FeedPage />
}

export default function App() {
  return (
    <AuthProvider>
      <div style={styles.app}>
        <Header />
        <main style={styles.main}>
          <Router />
        </main>
        <DebugPanel />
      </div>
    </AuthProvider>
  )
}

const styles: Record<string, React.CSSProperties> = {
  app: { minHeight: '100vh', background: '#f8fafc', fontFamily: 'system-ui, sans-serif' },
  header: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '12px 24px', background: '#fff',
    borderBottom: '1px solid #e2e8f0', position: 'sticky', top: 0, zIndex: 100,
  },
  logo: { fontWeight: 700, fontSize: 18, color: '#0f172a', textDecoration: 'none' },
  nav: { display: 'flex', alignItems: 'center', gap: 10 },
  avatar: { width: 28, height: 28, borderRadius: '50%' },
  username: { fontSize: 14, fontWeight: 500, color: '#1e293b' },
  rank: { fontSize: 12, color: '#64748b' },
  btn: {
    padding: '6px 14px', borderRadius: 6, border: '1px solid #e2e8f0',
    background: '#fff', cursor: 'pointer', fontSize: 13, color: '#374151',
  },
  btnPrimary: {
    background: '#1e293b', color: '#fff', border: 'none',
  },
  main: { padding: '24px 0' },
}
