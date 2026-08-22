import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useParams } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { TopNav } from './components/TopNav'
import { LeftPanel } from './components/LeftPanel'
import { RightPanel } from './components/RightPanel'
import { BottomNav } from './components/BottomNav'
import { ToastContainer } from './components/Toast'
import { CreateShipModal } from './components/CreateShipModal'
import { PageLoader } from './components/common/Loading'

// Pages
import { FeedPage } from './pages/FeedPage'
import { AuthCallback } from './pages/AuthCallback'
import { AuthPage } from './pages/AuthPage'
import { LandingPage } from './pages/LandingPage'
import { ShipDetailPage } from './pages/ShipDetailPage'
import { ProfilePage } from './pages/ProfilePage'
import { ProjectListPage } from './pages/ProjectListPage'
import { ProjectDetailPage } from './pages/ProjectDetailPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { SettingsPage } from './pages/SettingsPage'
import { ErrorPage } from './pages/ErrorPage'
import { EmptyStatePage } from './pages/EmptyStatePage'
import { PeoplePage } from './pages/PeoplePage'
import { AboutPage } from './pages/AboutPage'
import { HelpPage, PrivacyPage, TermsPage } from './pages/InfoPages'
import { SearchPage } from './pages/SearchPage'
import { CollaborationsPage } from './pages/CollaborationsPage'
import { AuthenticatedHomePage } from './components/home/AuthenticatedHomePage'
import {
  AdminAccessRequired,
  AdminAuditLogsPage,
  AdminDashboardPage,
  AdminForbiddenPage,
  AdminReportsPage,
  AdminUserDetailPage,
  AdminUsersPage,
} from './pages/admin/AdminConsole'

function LoadingScreen() {
  return <PageLoader message="Preparing your builder workspace..." />
}

function AppShell({
  children,
  rightPanel = true,
  leftPanel = true,
  wideMain = false,
}: {
  children: React.ReactNode
  rightPanel?: boolean
  leftPanel?: boolean
  wideMain?: boolean
}) {
  const { user } = useAuth()
  const [showCreateShip, setShowCreateShip] = useState(false)

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg)' }}>
      <TopNav />

      {/* 3-column LinkedIn layout */}
      <div style={{
        maxWidth: 'var(--layout-max)',
        margin: '0 auto',
        padding: 'var(--space-4) var(--space-4)',
        display: 'flex',
        gap: 'var(--layout-gap)',
        alignItems: 'flex-start',
        minHeight: 'calc(100vh - var(--header-height))',
      }}>
        {/* Left panel — profile card (desktop only) */}
        {user && leftPanel && (
          <div className="side-nav-desktop" style={{ position: 'sticky', top: 'calc(var(--header-height) + 16px)' }}>
            <LeftPanel />
          </div>
        )}

        {/* Center — main content */}
        <main style={{
          flex: 1,
          minWidth: 0,
          maxWidth: wideMain ? 'none' : 'var(--col-center-width)',
          paddingBottom: 80,
        }}>
          {children}
        </main>

        {/* Right panel — trending (desktop only) */}
        {rightPanel && (
          <div className="side-nav-desktop" style={{ position: 'sticky', top: 'calc(var(--header-height) + 16px)' }}>
            <RightPanel />
          </div>
        )}
      </div>

      {/* Mobile bottom nav */}
      <BottomNav />

      <CreateShipModal open={showCreateShip} onClose={() => setShowCreateShip(false)} />
    </div>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <LoadingScreen />
  if (!user) return <Navigate to="/" />
  return <AppShell>{children}</AppShell>
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <LoadingScreen />
  if (!user) return <AdminAccessRequired />
  if (!user.roles?.some(role => role === 'ADMIN' || role === 'ROLE_ADMIN')) return <AdminForbiddenPage />
  return <>{children}</>
}

function ProtectedHomeRoute() {
  const { user, loading } = useAuth()
  if (loading) return <LoadingScreen />
  if (!user) return <Navigate to="/" />
  return (
    <AppShell leftPanel={false} rightPanel={false} wideMain>
      <AuthenticatedHomePage />
    </AppShell>
  )
}

function PublicWithShell({ children }: { children: React.ReactNode }) {
  const { loading } = useAuth()
  if (loading) return <LoadingScreen />
  return <AppShell>{children}</AppShell>
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <LoadingScreen />
  if (user) return <Navigate to="/feed" />
  return <>{children}</>
}

function CreateShipRoute() {
  const [open, setOpen] = useState(true)
  const { user } = useAuth()

  if (!user) return <Navigate to="/" />
  if (user.banned) {
    return (
      <AppShell>
        <div className="blocked-write-panel" role="status">
          <p className="home-eyebrow">Writes blocked</p>
          <h1>Ship creation is unavailable for this account.</h1>
          <p>You can still browse public ships, view builders, and manage your session.</p>
        </div>
        <FeedPage />
      </AppShell>
    )
  }

  return (
    <AppShell>
      <FeedPage />
      <CreateShipModal open={open} onClose={() => {
        setOpen(false)
        window.history.back()
      }} />
    </AppShell>
  )
}

function ContentRedirectRoute() {
  const { id } = useParams<{ id: string }>()
  return <Navigate to={id ? `/ships/${id}` : '/trending'} replace />
}

function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <div style={{ maxWidth: 600, margin: '0 auto', padding: 'var(--space-10) var(--space-4)', textAlign: 'center' }}>
      <h1 style={{ fontSize: 'var(--text-2xl)', fontWeight: 700, margin: '0 0 var(--space-3) 0' }}>{title}</h1>
      <p style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-secondary)', margin: 0 }}>{description}</p>
    </div>
  )
}

function AppRoutes() {
  return (
    <Routes>
      {/* Public (no shell) */}
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route path="/" element={<PublicRoute><LandingPage /></PublicRoute>} />
      <Route path="/auth" element={<PublicRoute><AuthPage /></PublicRoute>} />
      <Route path="/signup" element={<PublicRoute><AuthPage /></PublicRoute>} />

      {/* Protected */}
      <Route path="/feed" element={<ProtectedHomeRoute />} />
      <Route path="/feed/personalized" element={<ProtectedRoute><FeedPage /></ProtectedRoute>} />
      <Route path="/feed/latest" element={<ProtectedRoute><FeedPage feedType="latest" /></ProtectedRoute>} />
      <Route path="/feed/following" element={<ProtectedRoute><FeedPage feedType="following" /></ProtectedRoute>} />
      <Route path="/ships/new" element={<CreateShipRoute />} />
      <Route path="/ships/:id" element={<PublicWithShell><ShipDetailPage /></PublicWithShell>} />
      <Route path="/content/:id" element={<ContentRedirectRoute />} />
      <Route path="/profile/:id" element={<PublicWithShell><ProfilePage /></PublicWithShell>} />
      <Route path="/projects" element={<ProtectedRoute><ProjectListPage /></ProtectedRoute>} />
      <Route path="/projects/:id" element={<PublicWithShell><ProjectDetailPage /></PublicWithShell>} />
      <Route path="/notifications" element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>} />
      <Route path="/collaborations" element={<ProtectedRoute><CollaborationsPage /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
      <Route path="/latest" element={<PublicWithShell><FeedPage feedType="latest" /></PublicWithShell>} />
      <Route path="/trending" element={<PublicWithShell><FeedPage feedType="trending" /></PublicWithShell>} />
      <Route path="/search" element={<PublicWithShell><SearchPage /></PublicWithShell>} />
      <Route path="/people" element={<PublicWithShell><PeoplePage /></PublicWithShell>} />
      <Route path="/learning" element={<PublicWithShell><PlaceholderPage title="Learning" description="Discover educational resources and content from builders. Coming soon." /></PublicWithShell>} />
      <Route path="/shipscore" element={<PublicWithShell><PlaceholderPage title="ShipScore" description="Understand reputation, ranking, and the builder leaderboard. Coming soon." /></PublicWithShell>} />
      <Route path="/about" element={<PublicWithShell><AboutPage /></PublicWithShell>} />
      <Route path="/help" element={<PublicWithShell><HelpPage /></PublicWithShell>} />
      <Route path="/privacy" element={<PublicWithShell><PrivacyPage /></PublicWithShell>} />
      <Route path="/terms" element={<PublicWithShell><TermsPage /></PublicWithShell>} />

      <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
      <Route path="/admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
      <Route path="/admin/users/:userId" element={<AdminRoute><AdminUserDetailPage /></AdminRoute>} />
      <Route path="/admin/reports" element={<AdminRoute><AdminReportsPage /></AdminRoute>} />
      <Route path="/admin/audit-logs" element={<AdminRoute><AdminAuditLogsPage /></AdminRoute>} />

      {/* Utility */}
      <Route path="/error/:code" element={<ErrorPage />} />
      <Route path="/empty" element={<EmptyStatePage />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
        <ToastContainer />
      </BrowserRouter>
    </AuthProvider>
  )
}
