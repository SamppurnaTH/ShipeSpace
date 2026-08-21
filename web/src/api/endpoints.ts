import { api } from './client'

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first?: boolean
  last?: boolean
  empty?: boolean
}

export interface AdminUser {
  id: string
  githubId?: string
  username: string
  email?: string
  avatarUrl?: string
  bio?: string
  location?: string
  websiteUrl?: string
  followerCount?: number
  followingCount?: number
  projectCount?: number
  shipScore?: number
  level?: number
  rank?: string
  roles?: string[]
  banned?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface AdminReport {
  id: string
  reporterId?: string
  targetId?: string
  targetType?: 'SHIP' | 'PROJECT' | 'USER' | 'COMMENT'
  reason?: 'SPAM' | 'HARASSMENT' | 'INAPPROPRIATE_CONTENT' | 'MISINFORMATION' | 'OTHER'
  details?: string
  status?: 'PENDING' | 'REVIEWED' | 'DISMISSED' | 'ACTION_TAKEN'
  resolvedBy?: string
  resolutionNote?: string
  createdAt?: string
  resolvedAt?: string
}

export interface AdminStats {
  totalUsers: number
  pendingReports: number
}

export interface AdminAuditLog {
  id: string
  eventType: string
  userId?: string
  entityId?: string
  previousState?: string
  newState?: string
  metadata?: string
  timestamp?: string
}

export interface SearchUserResult {
  id: string
  username: string
  avatarUrl?: string
  bio?: string
  rank?: string
}

export interface SearchShipResult {
  id: string
  content?: string
  createdAt?: string
  likeCount?: number
  commentCount?: number
  imageUrls?: unknown[]
  images?: unknown[]
  externalLinks?: { url: string; type?: string }[]
  user?: {
    id?: string
    username?: string
    avatarUrl?: string
    rank?: string
    level?: number
  }
  project?: {
    id?: string
    name?: string
    techTags?: string[]
  }
}

export interface SearchResponse {
  users: SearchUserResult[]
  ships: SearchShipResult[]
}

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

export const getUserProfile = (userId: string) =>
  api.get(`/users/${userId}`)

export const updateMyProfile = (data: { bio?: string; location?: string; websiteUrl?: string }) =>
  api.put('/users/me', data)

export const getMyActivity = (page = 0, size = 20) =>
  api.get('/users/me/activity', { params: { page, size } })

export const followUser = (userId: string) =>
  api.post(`/social/follow/${userId}`)

export const unfollowUser = (userId: string) =>
  api.delete(`/social/follow/${userId}`)

export const getFollowers = (page = 0, size = 20) =>
  api.get('/social/followers', { params: { page, size } })

export const getFollowing = (page = 0, size = 20) =>
  api.get('/social/following', { params: { page, size } })

export const getIncomingCollabs = (page = 0, size = 20) =>
  api.get('/collaborations/incoming', { params: { page, size } })

export const getSentCollabs = (page = 0, size = 20) =>
  api.get('/collaborations/sent', { params: { page, size } })

export const getRankSummary = (userId: string) =>
  api.get(`/v1/users/${userId}/rank-summary`)

export const getTrendingFeed = (page = 0, size = 20) =>
  api.get('/feeds/trending', { params: { page, size } })

export const getLatestFeed = (page = 0, size = 20) =>
  api.get('/feeds/latest', { params: { page, size } })

export const getTrendingByWindow = (window: string, page = 0, size = 20) =>
  api.get(`/feeds/trending/${window}`, { params: { page, size } })

export const getPersonalizedFeed = (page = 0, size = 20) =>
  api.get('/feeds/personalized', { params: { page, size } })

export const getFollowingFeed = (page = 0, size = 20) =>
  api.get('/feeds/following', { params: { page, size } })

export const createShip = (data: { projectId: string; content: string; externalLinks?: { url: string; type: string }[] }) =>
  api.post('/ships', data)

export const getShip = (shipId: string) =>
  api.get(`/ships/${shipId}`)

export const updateShip = (shipId: string, data: { content?: string; externalLinks?: { url: string; type: string }[] }) =>
  api.put(`/ships/${shipId}`, data)

export const deleteShip = (shipId: string) =>
  api.delete(`/ships/${shipId}`)

export const uploadShipImages = (shipId: string, formData: FormData) =>
  api.post(`/ships/${shipId}/images`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

export const likeShip = (shipId: string) =>
  api.put(`/ships/${shipId}/like`)

export const unlikeShip = (shipId: string) =>
  api.delete(`/ships/${shipId}/like`)

export const getLikeStatus = (shipId: string) =>
  api.get<boolean>(`/ships/${shipId}/like/status`)

export const getComments = (shipId: string, page = 0, size = 20) =>
  api.get(`/ships/${shipId}/comments`, { params: { page, size } })

export const addComment = (shipId: string, content: string) =>
  api.post(`/ships/${shipId}/comments`, { content })

export const deleteComment = (commentId: string) =>
  api.delete(`/ships/comments/${commentId}`)

export const upvoteComment = (commentId: string) =>
  api.put(`/ships/comments/${commentId}/upvote`)

export const removeCommentUpvote = (commentId: string) =>
  api.delete(`/ships/comments/${commentId}/upvote`)

export const createProject = (data: {
  name: string; description: string;
  techTags?: string[]; externalLinks?: { url: string; type: string }[]
}) =>
  api.post('/projects', data)

export const getProject = (projectId: string) =>
  api.get(`/projects/${projectId}`)

export const updateProject = (projectId: string, data: {
  name?: string; description?: string;
  techTags?: string[]; externalLinks?: { url: string; type: string }[]
}) =>
  api.put(`/projects/${projectId}`, data)

export const deleteProject = (projectId: string) =>
  api.delete(`/projects/${projectId}`)

export const getProjectShips = (projectId: string, page = 0, size = 20) =>
  api.get(`/projects/${projectId}/ships`, { params: { page, size } })

export const getMyProjects = () =>
  api.get('/users/me/projects')

export const getUserShips = (userId: string, page = 0, size = 20) =>
  api.get(`/users/${userId}/ships`, { params: { page, size } })

export const getUserProjects = (userId: string, page = 0, size = 20) =>
  api.get(`/users/${userId}/projects`, { params: { page, size } })

export const getNotifications = (page = 0, size = 20) =>
  api.get('/notifications', { params: { page, size } })

export const getUnreadCount = () =>
  api.get('/notifications/unread-count')

export const markNotificationRead = (notificationId: string) =>
  api.put(`/notifications/${notificationId}/read`)

export const markAllNotificationsRead = () =>
  api.put('/notifications/read-all')

export const requestCollaboration = (projectId: string, message?: string) =>
  api.post('/collaborations', { projectId, message })

export const getMyCollaborations = () =>
  api.get('/collaborations/incoming')

export const acceptCollaboration = (collabId: string) =>
  api.post(`/collaborations/${collabId}/accept`)

export const declineCollaboration = (collabId: string) =>
  api.post(`/collaborations/${collabId}/decline`)

export const searchShipsAndUsers = (query: string, size = 10, signal?: AbortSignal) =>
  api.get<SearchResponse>('/search', { params: { q: query, size }, signal })

// People Discovery
export interface BuilderDiscoveryResult {
  id: string
  username: string
  avatarUrl?: string
  bio?: string
  location?: string
  followerCount?: number
  projectCount?: number
  shipScore?: number
  level?: number
  rank?: string
  isFollowing?: boolean | null
  createdAt?: string
}

export type DiscoverySort = 'discover' | 'top' | 'new'

export const discoverBuilders = (page = 0, size = 20, sort: DiscoverySort = 'discover') =>
  api.get<PageResponse<BuilderDiscoveryResult>>('/users/discover', { params: { page, size, sort } })

export const searchBuilders = (q: string, page = 0, size = 20, signal?: AbortSignal) =>
  api.get<PageResponse<BuilderDiscoveryResult>>('/users/discover/search', { params: { q, page, size }, signal })

export const getAdminStats = () =>
  api.get<AdminStats>('/admin/stats')

export const getAdminUsers = (page = 0, size = 20) =>
  api.get<PageResponse<AdminUser>>('/admin/users', { params: { page, size } })

export const getAdminUser = (userId: string) =>
  api.get<AdminUser>(`/admin/users/${userId}`)

export const banAdminUser = (userId: string) =>
  api.patch<{ userId: string; banned: boolean }>(`/admin/users/${userId}/ban`, { banned: 'true' })

export const unbanAdminUser = (userId: string) =>
  api.patch<{ userId: string; banned: boolean }>(`/admin/users/${userId}/ban`, { banned: 'false' })

export const getAdminReports = (status = 'PENDING', page = 0, size = 20) =>
  api.get<PageResponse<AdminReport>>('/admin/reports', { params: { status, page, size } })

export const resolveAdminReport = (reportId: string, status: AdminReport['status'], note = '') =>
  api.patch<AdminReport>(`/admin/reports/${reportId}/resolve`, { status, note })

export const getAdminAuditLogs = (eventType = '', page = 0, size = 20) =>
  api.get<PageResponse<AdminAuditLog>>('/admin/audit-logs', {
    params: { ...(eventType ? { eventType } : {}), page, size },
  })
