import { api } from './client'

// ── Auth ──────────────────────────────────────────────────────────────────────

export const authGitHub = (code: string, state: string) =>
  api.post('/auth/github', { code, state })

export const getOAuthState = () =>
  api.get<{ state: string }>('/auth/state')

export const refreshToken = (refreshToken: string) =>
  api.post('/auth/refresh', { refreshToken })

export const getMe = () =>
  api.get('/auth/me')

export const logout = (refreshToken?: string) =>
  api.post('/auth/logout', refreshToken ? { refreshToken } : undefined)

// ── Feed ──────────────────────────────────────────────────────────────────────

export const getTrendingFeed = (page = 0, size = 20) =>
  api.get('/feeds/trending', { params: { page, size } })

export const getPersonalizedFeed = (page = 0, size = 20) =>
  api.get('/feeds/personalized', { params: { page, size } })

export const getFollowingFeed = (page = 0, size = 20) =>
  api.get('/feeds/following', { params: { page, size } })

// ── Engagement ────────────────────────────────────────────────────────────────

export const likeShip = (shipId: string) =>
  api.put(`/ships/${shipId}/like`)

export const unlikeShip = (shipId: string) =>
  api.delete(`/ships/${shipId}/like`)

export const getShip = (shipId: string) =>
  api.get(`/ships/${shipId}`)
