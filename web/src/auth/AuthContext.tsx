import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { getMe, logout as apiLogout } from '../api/endpoints'

interface User {
  id: string
  username: string
  avatarUrl: string
  email: string
  shipScore: number
  level: number
  rank: string
  bio?: string
  location?: string
  websiteUrl?: string
}

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (accessToken: string, refreshToken: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function clearTokens() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('access_token')
    if (!token) { setLoading(false); return }
    getMe()
      .then(res => setUser(res.data))
      .catch(() => clearTokens())
      .finally(() => setLoading(false))
  }, [])

  const login = async (accessToken: string, refreshToken: string): Promise<void> => {
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    try {
      const res = await getMe()
      setUser(res.data)
    } catch {
      // Tokens stored but /me failed — clear everything to avoid broken state
      clearTokens()
      throw new Error('Login succeeded but failed to load user profile. Please try again.')
    }
  }

  const doLogout = async (): Promise<void> => {
    const rt = localStorage.getItem('refresh_token')
    // Fire-and-forget server-side revocation — clear locally regardless of outcome
    try {
      await apiLogout(rt ?? undefined)
    } catch {
      // Intentionally swallowed: local session must always be cleared even if
      // the revocation request fails (e.g. network error, token already expired)
    } finally {
      clearTokens()
      setUser(null)
    }
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout: doLogout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
