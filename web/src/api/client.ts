import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const api = axios.create({ baseURL: BASE_URL })

export interface RequestMeta {
  url: string
  status: number
  latencyMs: number
  feedMode?: string
  rateLimitRemaining?: string
  error?: string
}

const listeners: ((meta: RequestMeta) => void)[] = []
export const onRequest = (fn: (meta: RequestMeta) => void) => { listeners.push(fn) }
const emit = (meta: RequestMeta) => listeners.forEach(fn => fn(meta))

// Stamp start time on every request
api.interceptors.request.use(config => {
  (config as any)._t = Date.now()
  return config
})

// Attach JWT
api.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Capture response metadata for debug panel
api.interceptors.response.use(
  res => {
    const latencyMs = (res.config as any)._t ? Date.now() - (res.config as any)._t : 0
    emit({
      url: res.config.url || '',
      status: res.status,
      latencyMs,
      feedMode: res.headers['x-feed-mode'],
      rateLimitRemaining: res.headers['x-ratelimit-remaining'],
    })
    return res
  },
  err => {
    const res = err.response
    const latencyMs = err.config?._t ? Date.now() - err.config._t : 0
    emit({
      url: err.config?.url || '',
      status: res?.status || 0,
      latencyMs,
      error: res?.data?.message || err.message,
    })
    if (res?.status === 401) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      window.location.href = '/'
    }
    return Promise.reject(err)
  }
)
