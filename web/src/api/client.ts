import axios, { type AxiosRequestConfig } from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const api = axios.create({ baseURL: BASE_URL, timeout: 15000 })

// ── Debug metadata ────────────────────────────────────────────────────────────
export interface RequestMeta {
  url: string; status: number; latencyMs: number
  feedMode?: string; rateLimitRemaining?: string; error?: string
}
const listeners: ((m: RequestMeta) => void)[] = []
export const onRequest = (fn: (m: RequestMeta) => void) => {
  listeners.push(fn)
  return () => { const i = listeners.indexOf(fn); if (i > -1) listeners.splice(i, 1) }
}
const emit = (m: RequestMeta) => listeners.forEach(fn => fn(m))

// ── Stamp start time ──────────────────────────────────────────────────────────
api.interceptors.request.use(config => { (config as any)._t = Date.now(); return config })

// ── Attach JWT ────────────────────────────────────────────────────────────────
api.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── Response: capture metadata + silent refresh on 401 ───────────────────────
let refreshing: Promise<void> | null = null

api.interceptors.response.use(
  res => {
    const ms = (res.config as any)._t ? Date.now() - (res.config as any)._t : 0
    emit({ url: res.config.url || '', status: res.status, latencyMs: ms,
      feedMode: res.headers['x-feed-mode'],
      rateLimitRemaining: res.headers['x-ratelimit-remaining'] })
    return res
  },
  async err => {
    const res = err.response
    const ms = err.config?._t ? Date.now() - err.config._t : 0
    emit({ url: err.config?.url || '', status: res?.status || 0, latencyMs: ms,
      error: res?.data?.message || err.message })

    // Silent token refresh on 401 (only once per request)
    if (res?.status === 401 && !err.config._retry) {
      err.config._retry = true
      const rt = localStorage.getItem('refresh_token')
      if (rt) {
        if (!refreshing) {
          refreshing = axios.post(`${BASE_URL}/auth/refresh`, { refreshToken: rt })
            .then(r => {
              localStorage.setItem('access_token', r.data.accessToken)
              localStorage.setItem('refresh_token', r.data.refreshToken)
            })
            .catch(() => {
              localStorage.removeItem('access_token')
              localStorage.removeItem('refresh_token')
              window.location.href = '/'
            })
            .finally(() => { refreshing = null })
        }
        await refreshing
        const newToken = localStorage.getItem('access_token')
        if (newToken) {
          err.config.headers.Authorization = `Bearer ${newToken}`
          return api(err.config as AxiosRequestConfig)
        }
      } else {
        localStorage.removeItem('access_token')
        window.location.href = '/'
      }
    }
    return Promise.reject(err)
  }
)
