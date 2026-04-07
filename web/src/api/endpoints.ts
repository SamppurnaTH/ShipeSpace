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

// ── Users ─────────────────────────────────────────────────────────────────────

export const getUserProfile = (userId: string) =>
  api.get(`/users/${userId}`)

export const updateMyProfile = (data: { bio?: string; location?: string; websiteUrl?: string }) =>
  api.put('/users/me', data)

export const getMyActivity = (page = 0, size = 20) =>
  api.get('/users/me/activity', { params: { page, size } })

// ── Social ────────────────────────────────────────────────────────────────────

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

// ── Feed ──────────────────────────────────────────────────────────────────────

export const getTrendingFeed = (page = 0, size = 20) =>
  api.get('/feeds/trending', { params: { page, size } })

export const getTrendingByWindow = (window: string, page = 0, size = 20) =>
  api.get(`/feeds/trending/${window}`, { params: { page, size } })

export const getPersonalizedFeed = (page = 0, size = 20) =>
  api.get('/feeds/personalized', { params: { page, size } })

export const getFollowingFeed = (page = 0, size = 20) =>
  api.get('/feeds/following', { params: { page, size } })

// ── Ships ─────────────────────────────────────────────────────────────────────

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

// ── Engagement ────────────────────────────────────────────────────────────────

export const likeShip = (shipId: string) =>
  api.put(`/ships/${shipId}/like`)

export const unlikeShip = (shipId: string) =>
  api.delete(`/ships/${shipId}/like`)

// ── Comments ──────────────────────────────────────────────────────────────────

export const getComments = (shipId: string, page = 0, size = 20) =>
  api.get(`/ships/${shipId}/comments`, { params: { page, size } })

export const addComment = (shipId: string, content: string) =>
  api.post(`/ships/${shipId}/comments`, { content })

export const deleteComment = (commentId: string) =>
  api.delete(`/ships/comments/${commentId}`)

export const upvoteComment = (commentId: string) =>
  api.put(`/ships/comments/${commentId}/upvote`)

// ── Projects ──────────────────────────────────────────────────────────────────

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

// ── Notifications ─────────────────────────────────────────────────────────────

export const getNotifications = (page = 0, size = 20) =>
  api.get('/notifications', { params: { page, size } })

export const getUnreadCount = () =>
  api.get('/notifications/unread-count')

export const markNotificationRead = (notificationId: string) =>
  api.put(`/notifications/${notificationId}/read`)

export const markAllNotificationsRead = () =>
  api.put('/notifications/read-all')

// ── Collaborations ────────────────────────────────────────────────────────────

export const requestCollaboration = (projectId: string, message?: string) =>
  api.post('/collaborations', { projectId, message })

export const getMyCollaborations = () =>
  api.get('/collaborations/incoming')

export const acceptCollaboration = (collabId: string) =>
  api.post(`/collaborations/${collabId}/accept`)

export const declineCollaboration = (collabId: string) =>
  api.post(`/collaborations/${collabId}/decline`)

// ── Search ────────────────────────────────────────────────────────────────────

export const searchShipsAndUsers = (query: string, size = 10) =>
  api.get<{ users: any[]; ships: any[] }>('/search', { params: { q: query, size } })
