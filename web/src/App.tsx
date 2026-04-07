import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { TopNav } from './components/TopNav'
import { SideNav } from './components/SideNav'
import { BottomNav } from './components/BottomNav'
import { ToastContainer } from './components/Toast'
import { CreateShipModal } from './components/CreateShipModal'
import { Spinner } from './components/Spinner'

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
import { DebugPage } from './pages/DebugPage'
import { SearchPage } from './pages/SearchPage'
import { CollaborationsPage } from './pages/CollaborationsPage'
import { ContentDetailPage } from './pages/ContentDetailPage'

function LoadingScreen() {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      minHeight: '100vh', gap: 16,
    }}>
      <span style={{ fontSize: 32 }}>🚀</span>
      <Spinner size={24} />
    </div>
  )
}

function AppShell({ children }: { children: React.ReactNode }) {
  const [showCreateShip, setShowCreateShip] = useState(false)

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg)' }}>
      <TopNav />
      <div style={{
        display: 'flex',
        minHeight: 'calc(100vh - 64px)',
      }}>
        {/* Desktop SideNav */}
        <div className="side-nav-desktop">
          <SideNav />
        </div>

        {/* Main content */}
        <main style={{
          flex: 1,
          paddingTop: 'var(--space-6)',
          paddingBottom: 80,
          minWidth: 0,
        }}>
          {children}
        </main>
      </div>

      {/* Mobile bottom nav */}
      <BottomNav />

      {/* Global create ship modal (triggered from SideNav CTA, navigating to /ships/new) */}
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

function AppRoutes() {
  return (
    <Routes>
      {/* Public (no shell) */}
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route path="/" element={<PublicRoute><LandingPage /></PublicRoute>} />
      <Route path="/auth" element={<PublicRoute><AuthPage /></PublicRoute>} />

      {/* Protected */}
      <Route path="/feed" element={<ProtectedRoute><FeedPage /></ProtectedRoute>} />
      <Route path="/feed/personalized" element={<ProtectedRoute><FeedPage /></ProtectedRoute>} />
      <Route path="/ships/new" element={<CreateShipRoute />} />
      <Route path="/ships/:id" element={<PublicWithShell><ShipDetailPage /></PublicWithShell>} />
      <Route path="/content/:id" element={<PublicWithShell><ContentDetailPage /></PublicWithShell>} />
      <Route path="/profile/:id" element={<PublicWithShell><ProfilePage /></PublicWithShell>} />
      <Route path="/projects" element={<ProtectedRoute><ProjectListPage /></ProtectedRoute>} />
      <Route path="/projects/:id" element={<PublicWithShell><ProjectDetailPage /></PublicWithShell>} />
      <Route path="/notifications" element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>} />
      <Route path="/collaborations" element={<ProtectedRoute><CollaborationsPage /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
      <Route path="/trending" element={<PublicWithShell><FeedPage /></PublicWithShell>} />
      <Route path="/search" element={<PublicWithShell><SearchPage /></PublicWithShell>} />
      <Route path="/debug" element={<ProtectedRoute><DebugPage /></ProtectedRoute>} />

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
