import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { getGitHubLoginErrorMessage, startGitHubLogin } from '../auth/githubLogin'
import { ButtonLoader } from '../components/common/Loading'

type AuthMode = 'signin' | 'signup' | 'forgot'

export function AuthPage() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const mode = useMemo<AuthMode>(() => {
    const queryMode = searchParams.get('mode')
    if (queryMode === 'signup') return 'signup'
    if (queryMode === 'signin') return 'signin'
    if (queryMode === 'forgot') return 'forgot'
    return location.pathname === '/signup' ? 'signup' : 'signin'
  }, [location.pathname, searchParams])
  const [githubLoading, setGithubLoading] = useState(false)
  const [error, setError] = useState('')
  const [acceptedTerms, setAcceptedTerms] = useState(false)

  useEffect(() => {
    setError('')
    if (mode !== 'signup') setAcceptedTerms(false)
  }, [mode])

  const setMode = (nextMode: AuthMode) => {
    setSearchParams({ mode: nextMode })
  }

  const handleGitHubLogin = async () => {
    if (mode === 'signup' && !acceptedTerms) {
      setError('Please accept the Terms of Use and Privacy Policy to continue.')
      return
    }
    setGithubLoading(true)
    setError('')
    try {
      await startGitHubLogin()
    } catch (err) {
      setError(getGitHubLoginErrorMessage(err))
      setGithubLoading(false)
    }
  }

  return (
    <main className="auth-page" aria-labelledby="auth-title">
      <Link to="/" className="auth-brand-fixed" aria-label="KREVO home">
        <span className="auth-mark" aria-hidden="true"><ShipSpaceMark /></span>
      </Link>

      <section className="auth-layout">
        <div className="auth-card">
          <div className="auth-heading">
            <h1 id="auth-title">{mode === 'signup' ? 'Join KREVO' : 'Sign in'}</h1>
            <p>
              {mode === 'signin' && (
                <>New to KREVO? <button type="button" onClick={() => setMode('signup')}>Join now</button></>
              )}
              {mode === 'signup' && (
                <>Already building here? <button type="button" onClick={() => setMode('signin')}>Sign in</button></>
              )}
            </p>
          </div>

          <div className="auth-tabs" role="tablist" aria-label="Authentication mode">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'signin'}
              className={mode === 'signin' ? 'auth-tab-active' : ''}
              onClick={() => setMode('signin')}
            >
              Sign in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'signup'}
              className={mode === 'signup' ? 'auth-tab-active' : ''}
              onClick={() => setMode('signup')}
            >
              Sign up
            </button>
          </div>

          <button
            onClick={handleGitHubLogin}
            disabled={githubLoading || (mode === 'signup' && !acceptedTerms)}
            className="auth-provider-button auth-provider-primary"
          >
            {githubLoading ? (
              <ButtonLoader label="Connecting..." />
            ) : (
              <>
                <GitHubIcon />
                {mode === 'signin' ? 'Continue with GitHub' : 'Join with GitHub'}
              </>
            )}
          </button>

          <p className="auth-legal-copy">
            KREVO uses GitHub to verify builder identity, bootstrap your profile, and keep sign-in simple.
          </p>

          {mode === 'signup' && (
            <div className="auth-checkbox auth-terms-checkbox">
              <input
                id="auth-terms"
                type="checkbox"
                checked={acceptedTerms}
                onChange={event => {
                  setAcceptedTerms(event.target.checked)
                  if (event.target.checked) setError('')
                }}
              />
              <label htmlFor="auth-terms">
                I agree to KREVO&apos;s <Link to="/terms">Terms of Use</Link> and <Link to="/privacy">Privacy Policy</Link>.
              </label>
            </div>
          )}

          {error && <p className="home-form-error" role="alert">{error}</p>}

          <p className="auth-switch-copy">
            {mode === 'signin' ? 'New to KREVO?' : mode === 'signup' ? 'Already have an account?' : ''}
            {' '}
            {mode !== 'forgot' && (
              <button type="button" onClick={() => setMode(mode === 'signin' ? 'signup' : 'signin')}>
                {mode === 'signin' ? 'Create an account' : 'Sign in'}
              </button>
            )}
          </p>
        </div>
      </section>
    </main>
  )
}

function GitHubIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.44 9.8 8.21 11.39.6.11.82-.26.82-.58 0-.28-.01-1.03-.02-2.02-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.75.08-.73.08-.73 1.2.08 1.84 1.24 1.84 1.24 1.07 1.83 2.81 1.3 3.49.99.11-.78.42-1.3.76-1.6-2.66-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.12-.3-.54-1.52.12-3.18 0 0 1.01-.32 3.3 1.23A11.5 11.5 0 0 1 12 5.31c1.02.01 2.05.14 3.01.41 2.29-1.55 3.3-1.23 3.3-1.23.66 1.66.24 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.62-5.48 5.92.43.37.81 1.1.81 2.22 0 1.6-.01 2.89-.01 3.28 0 .32.22.69.83.57A12.01 12.01 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
    </svg>
  )
}

function ShipSpaceMark() {
  return <img src="/logo.png" alt="KREVO Logo" />
}



