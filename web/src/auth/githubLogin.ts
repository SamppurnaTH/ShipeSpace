import { getOAuthState } from '../api/endpoints'

const GITHUB_AUTH_URL = 'https://github.com/login/oauth/authorize'
const AUTH_RETURN_TO_KEY = 'shipspace_auth_return_to'
const PLACEHOLDER_CLIENT_IDS = new Set([
  '',
  'CHANGE_ME',
  'CHANGE_ME_GITHUB_CLIENT_ID',
  'your_github_oauth_app_client_id',
])

function getGitHubClientId() {
  const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID?.trim() ?? ''
  if (PLACEHOLDER_CLIENT_IDS.has(clientId)) {
    throw new Error('GitHub login is not configured. Set VITE_GITHUB_CLIENT_ID and restart the frontend dev server.')
  }
  return clientId
}

function getRedirectUri() {
  return import.meta.env.VITE_GITHUB_REDIRECT_URI?.trim() || `${window.location.origin}/auth/callback`
}

function normalizeReturnTo(returnTo?: string) {
  if (!returnTo || !returnTo.startsWith('/') || returnTo.startsWith('//')) return ''
  return returnTo
}

export function consumeAuthReturnTo() {
  const returnTo = normalizeReturnTo(sessionStorage.getItem(AUTH_RETURN_TO_KEY) || '')
  sessionStorage.removeItem(AUTH_RETURN_TO_KEY)
  return returnTo
}

export async function startGitHubLogin(returnTo?: string) {
  const safeReturnTo = normalizeReturnTo(returnTo)
  if (safeReturnTo) {
    sessionStorage.setItem(AUTH_RETURN_TO_KEY, safeReturnTo)
  }

  const res = await getOAuthState()
  const state = res.data.state?.trim()
  if (!state) {
    throw new Error('GitHub login could not start because the auth state response was empty.')
  }

  const url = new URL(GITHUB_AUTH_URL)
  url.searchParams.set('client_id', getGitHubClientId())
  url.searchParams.set('redirect_uri', getRedirectUri())
  url.searchParams.set('state', state)
  url.searchParams.set('scope', 'read:user,user:email')

  window.location.assign(url.toString())
}

export function getGitHubLoginErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) return error.message
  return 'GitHub login is temporarily unavailable. You can still explore public ships.'
}
