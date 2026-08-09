import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate, useParams } from 'react-router-dom'
import {
  banAdminUser,
  getAdminReports,
  getAdminStats,
  getAdminUser,
  getAdminUsers,
  resolveAdminReport,
  unbanAdminUser,
  type AdminReport,
  type AdminStats,
  type AdminUser,
  type PageResponse,
} from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { toastError, toastSuccess } from '../../components/Toast'
import { ButtonLoader, Skeleton } from '../../components/common/Loading'
import { getGitHubLoginErrorMessage, startGitHubLogin } from '../../auth/githubLogin'

type Filter = 'all' | 'active' | 'banned' | 'admin'
type AdminSection = 'dashboard' | 'users' | 'reports'
type Action = 'ban' | 'unban'

interface AdminState {
  users: AdminUser[]
  page: PageResponse<AdminUser> | null
  stats: AdminStats | null
  reports: AdminReport[]
  loadingUsers: boolean
  loadingStats: boolean
  loadingReports: boolean
  error: string
  lastUpdatedAt: string
}

const emptyState: AdminState = {
  users: [],
  page: null,
  stats: null,
  reports: [],
  loadingUsers: true,
  loadingStats: true,
  loadingReports: true,
  error: '',
  lastUpdatedAt: '',
}

export function AdminDashboardPage() {
  return (
    <AdminLayout active="dashboard">
      <AdminDashboard />
    </AdminLayout>
  )
}

export function AdminUsersPage() {
  return (
    <AdminLayout active="users">
      <AdminUsers />
    </AdminLayout>
  )
}

export function AdminUserDetailPage() {
  return (
    <AdminLayout active="users">
      <AdminUserDetail />
    </AdminLayout>
  )
}

export function AdminReportsPage() {
  return (
    <AdminLayout active="reports">
      <AdminReports />
    </AdminLayout>
  )
}

export function AdminAccessRequired() {
  const [loading, setLoading] = useState(false)

  const handleLogin = async () => {
    setLoading(true)
    try {
      await startGitHubLogin('/admin')
    } catch (error) {
      toastError(getGitHubLoginErrorMessage(error))
      setLoading(false)
    }
  }

  return (
    <main className="admin-access-page">
      <section className="admin-access-card" aria-labelledby="admin-access-title">
        <span className="admin-access-icon"><Icon name="settings" size={24} /></span>
        <p className="admin-eyebrow">Admin console</p>
        <h1 id="admin-access-title">Admin access requires sign in.</h1>
        <p>Use your GitHub account so ShipSpace can verify whether your role includes admin access.</p>
        <button type="button" className="admin-primary-button" onClick={handleLogin} disabled={loading}>
          {loading ? <ButtonLoader label="Connecting..." /> : 'Continue with GitHub'}
        </button>
        <Link className="admin-secondary-link" to="/">Back to ShipSpace</Link>
      </section>
    </main>
  )
}

export function AdminForbiddenPage() {
  return (
    <main className="admin-access-page">
      <section className="admin-access-card" role="alert" aria-labelledby="admin-forbidden-title">
        <span className="admin-access-icon admin-access-danger"><Icon name="alert" size={24} /></span>
        <p className="admin-eyebrow">Restricted area</p>
        <h1 id="admin-forbidden-title">You do not have access to the admin console.</h1>
        <p>This area is restricted to ShipSpace administrators.</p>
        <Link className="admin-primary-link" to="/feed">Back to ShipSpace</Link>
      </section>
    </main>
  )
}

function AdminLayout({ active, children }: { active: AdminSection; children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="admin-shell">
      <header className="admin-topbar">
        <Link to="/admin" className="admin-brand" aria-label="ShipSpace admin home">
          <span className="admin-brand-mark"><Icon name="ship" size={22} /></span>
          <span>
            <strong>ShipSpace Admin</strong>
            <small>Community operations</small>
          </span>
        </Link>
        <div className="admin-topbar-actions">
          <Link to="/feed" className="admin-ghost-link">Builder app</Link>
          <button type="button" className="admin-avatar-button" onClick={() => navigate(`/profile/${user?.username}`)}>
            {user?.avatarUrl ? <img src={user.avatarUrl} alt={`${user.username} avatar`} /> : <Icon name="user" size={18} />}
            <span>{user?.username}</span>
          </button>
          <button type="button" className="admin-ghost-button" onClick={() => void logout()}>Logout</button>
        </div>
      </header>

      <div className="admin-workspace">
        <aside className="admin-sidebar" aria-label="Admin navigation">
          <nav>
            <AdminNavLink to="/admin" label="Dashboard" icon="home" active={active === 'dashboard'} end />
            <AdminNavLink to="/admin/users" label="Users" icon="user" active={active === 'users'} />
            <AdminNavLink to="/admin/reports" label="Reports" icon="alert" active={active === 'reports'} />
          </nav>
          <div className="admin-sidebar-note">
            <strong>REAL data only</strong>
            <span>Audit logs and advanced analytics are post-MVP until backend APIs exist.</span>
          </div>
        </aside>
        <main className="admin-main">
          {children}
        </main>
      </div>
    </div>
  )
}

function AdminNavLink({
  to,
  label,
  icon,
  active,
  end,
}: {
  to: string
  label: string
  icon: Parameters<typeof Icon>[0]['name']
  active: boolean
  end?: boolean
}) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => `admin-nav-link ${isActive || active ? 'admin-nav-link-active' : ''}`}>
      <Icon name={icon} size={18} />
      <span>{label}</span>
    </NavLink>
  )
}

function AdminDashboard() {
  const [state, setState] = useState<AdminState>(emptyState)

  const load = useCallback(async () => {
    setState(prev => ({ ...prev, loadingUsers: true, loadingStats: true, loadingReports: true, error: '' }))
    try {
      const [stats, users, reports] = await Promise.all([
        getAdminStats(),
        getAdminUsers(0, 10),
        getAdminReports('PENDING', 0, 5),
      ])
      setState({
        users: users.data.content ?? [],
        page: users.data,
        stats: stats.data,
        reports: reports.data.content ?? [],
        loadingUsers: false,
        loadingStats: false,
        loadingReports: false,
        error: '',
        lastUpdatedAt: new Date().toISOString(),
      })
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loadingUsers: false,
        loadingStats: false,
        loadingReports: false,
        error: adminErrorMessage(error),
        lastUpdatedAt: prev.lastUpdatedAt,
      }))
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const bannedOnPage = state.users.filter(user => user.banned).length
  const adminsOnPage = state.users.filter(user => hasAdminRole(user)).length

  return (
    <section className="admin-page" aria-labelledby="admin-dashboard-title">
      <AdminPageHeader
        eyebrow="Operations"
        title="Admin Dashboard"
        description="A real-time entry point for user management and moderation work supported by the current backend."
        meta={state.lastUpdatedAt ? `Last updated ${relativeTime(state.lastUpdatedAt)}` : 'Loading live data'}
        action={(
          <div className="admin-header-actions">
            <button type="button" className="admin-secondary-button" onClick={() => void load()}>Refresh</button>
            <Link className="admin-primary-link" to="/admin/users">Manage users</Link>
          </div>
        )}
      />

      {state.error && <AdminAlert message={state.error} onRetry={load} />}

      <div className="admin-metric-grid" aria-busy={state.loadingStats}>
        <MetricCard label="Total users" value={state.stats?.totalUsers} loading={state.loadingStats} classification="REAL" />
        <MetricCard label="Pending reports" value={state.stats?.pendingReports} loading={state.loadingStats} classification="REAL" />
        <MetricCard label="Banned in current result" value={bannedOnPage} loading={state.loadingUsers} classification="DERIVED" />
        <MetricCard label="Admins in current result" value={adminsOnPage} loading={state.loadingUsers} classification="DERIVED" />
      </div>

      <div className="admin-dashboard-grid">
        <AdminPanel title="Recent users" action={<Link to="/admin/users">View all</Link>}>
          {state.loadingUsers ? <AdminSkeletonRows count={4} /> : (
            <div className="admin-mini-list">
              {state.users.length === 0 && <AdminEmpty title="No users loaded" body="The backend returned an empty user page." />}
              {state.users.slice(0, 5).map(user => <UserMiniRow key={user.id} user={user} />)}
            </div>
          )}
        </AdminPanel>

        <AdminPanel title="Pending reports" action={<Link to="/admin/reports">Review reports</Link>}>
          {state.loadingReports ? <AdminSkeletonRows count={4} /> : (
            <div className="admin-mini-list">
              {state.reports.length === 0 && (
                <AdminEmpty
                  title="No pending reports"
                  body="There are no moderation reports waiting for review."
                  action={<Link className="admin-secondary-link" to="/admin/reports">View all reports</Link>}
                />
              )}
              {state.reports.map(report => <ReportMiniRow key={report.id} report={report} />)}
            </div>
          )}
        </AdminPanel>
      </div>
    </section>
  )
}

function AdminUsers() {
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [page, setPage] = useState<PageResponse<AdminUser> | null>(null)
  const [pageNumber, setPageNumber] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [pendingAction, setPendingAction] = useState<{ action: Action; user: AdminUser } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await getAdminUsers(pageNumber, 20)
      setUsers(response.data.content ?? [])
      setPage(response.data)
    } catch (error: any) {
      setError(adminErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [pageNumber])

  useEffect(() => {
    void load()
  }, [load])

  const filteredUsers = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return users.filter(user => {
      const matchesQuery = !normalized ||
        [user.username, user.email, user.githubId, user.id].some(value => value?.toLowerCase().includes(normalized))
      const matchesFilter =
        filter === 'all' ||
        (filter === 'active' && !user.banned) ||
        (filter === 'banned' && user.banned) ||
        (filter === 'admin' && hasAdminRole(user))
      return matchesQuery && matchesFilter
    })
  }, [users, query, filter])

  const updateUserStatus = (userId: string, banned: boolean) => {
    setUsers(prev => prev.map(user => user.id === userId ? { ...user, banned } : user))
  }

  return (
    <section className="admin-page" aria-labelledby="admin-users-title">
      <AdminPageHeader
        eyebrow="People"
        title="User Management"
        description="Search the loaded admin user page, inspect roles and status, and safely ban or unban accounts."
      />

      <AdminSafetyNote />

      {error && <AdminAlert message={error} onRetry={load} />}

      <div className="admin-toolbar">
        <label className="admin-search-field">
          <span>Search users</span>
          <div>
            <Icon name="search" size={18} />
            <input value={query} onChange={event => setQuery(event.target.value)} placeholder="Username, email, GitHub ID..." />
          </div>
        </label>
        <fieldset className="admin-filter-group">
          <legend>User filters</legend>
          {(['all', 'active', 'banned', 'admin'] as Filter[]).map(item => (
            <button
              key={item}
              type="button"
              className={filter === item ? 'admin-filter-active' : ''}
              onClick={() => setFilter(item)}
            >
              {capitalize(item)}
            </button>
          ))}
        </fieldset>
      </div>

      <div className="admin-table-card" aria-busy={loading}>
        {loading ? <AdminSkeletonRows count={8} /> : (
          <>
            {filteredUsers.length === 0 ? (
              <AdminEmpty title="No users match this view" body="Adjust the search or filter to inspect another part of the loaded admin page." />
            ) : (
              <div className="admin-user-table" role="table" aria-label="User management table">
                <div className="admin-user-row admin-user-head" role="row">
                  <span role="columnheader">User</span>
                  <span role="columnheader">Role</span>
                  <span role="columnheader">Status</span>
                  <span role="columnheader">Projects</span>
                  <span role="columnheader">ShipScore</span>
                  <span role="columnheader">Joined</span>
                  <span role="columnheader">Actions</span>
                </div>
                {filteredUsers.map(user => (
                  <UserTableRow
                    key={user.id}
                    user={user}
                    currentUserId={currentUser?.id}
                    onAction={action => setPendingAction({ action, user })}
                  />
                ))}
              </div>
            )}
          </>
        )}
      </div>

      <AdminPagination page={page} onPrev={() => setPageNumber(value => Math.max(0, value - 1))} onNext={() => setPageNumber(value => value + 1)} />

      <BanConfirmModal
        pending={pendingAction}
        currentUserId={currentUser?.id}
        onClose={() => setPendingAction(null)}
        onSuccess={updateUserStatus}
      />
    </section>
  )
}

function AdminUserDetail() {
  const { user: currentUser } = useAuth()
  const { userId = '' } = useParams<{ userId: string }>()
  const [user, setUser] = useState<AdminUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pendingAction, setPendingAction] = useState<{ action: Action; user: AdminUser } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await getAdminUser(userId)
      setUser(response.data)
    } catch (error: any) {
      setError(adminErrorMessage(error, 'Could not load this user.'))
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    void load()
  }, [load])

  const updateUserStatus = (_userId: string, banned: boolean) => {
    setUser(prev => prev ? { ...prev, banned } : prev)
  }

  return (
    <section className="admin-page" aria-labelledby="admin-user-detail-title">
      <AdminPageHeader
        eyebrow="User detail"
        title="Account Review"
        description="Review identity, role, public profile state, and moderation controls for a single account."
        action={<Link className="admin-secondary-link" to="/admin/users">Back to users</Link>}
      />

      <AdminSafetyNote />

      {loading && <AdminSkeletonRows count={6} />}
      {!loading && error && <AdminAlert message={error} onRetry={load} />}
      {!loading && !error && !user && <AdminEmpty title="User not found" body="No user record was returned for this identifier." />}

      {user && (
        <div className="admin-detail-grid">
          <section className="admin-detail-card">
            <div className="admin-detail-identity">
              {user.avatarUrl ? <img src={user.avatarUrl} alt={`${user.username} avatar`} /> : <span><Icon name="user" size={26} /></span>}
              <div>
                <h2 id="admin-user-detail-title">{user.username}</h2>
                <p>{user.email || 'No email available'}</p>
              </div>
            </div>
            <div className="admin-badge-row">
              <StatusBadge banned={Boolean(user.banned)} />
              <RoleBadge admin={hasAdminRole(user)} />
            </div>
            <dl className="admin-definition-list">
              <div><dt>User ID</dt><dd>{user.id}</dd></div>
              <div><dt>GitHub ID</dt><dd>{user.githubId || 'Not available'}</dd></div>
              <div><dt>ShipScore</dt><dd>{user.shipScore ?? 0}</dd></div>
              <div><dt>Projects</dt><dd>{user.projectCount ?? 0}</dd></div>
              <div><dt>Joined</dt><dd>{formatDate(user.createdAt)}</dd></div>
              <div><dt>Location</dt><dd>{user.location || 'Not provided'}</dd></div>
            </dl>
          </section>

          <section className="admin-detail-card">
            <h2>Admin actions</h2>
            <p className="admin-muted">Ban/unban changes are sent to the real backend and immediately affect write access.</p>
            <div className="admin-action-stack">
              <Link className="admin-secondary-link" to={`/profile/${user.username}`}>View public profile</Link>
              {!user.banned && (
                <button
                  type="button"
                  className="admin-danger-button"
                  disabled={user.id === currentUser?.id}
                  onClick={() => setPendingAction({ action: 'ban', user })}
                >
                  Ban user
                </button>
              )}
              {user.banned && (
                <button
                  type="button"
                  className="admin-primary-button"
                  disabled={user.id === currentUser?.id}
                  onClick={() => setPendingAction({ action: 'unban', user })}
                >
                  Unban user
                </button>
              )}
              {user.id === currentUser?.id && <p className="admin-inline-warning" role="status">You cannot ban or unban your own admin account.</p>}
            </div>
          </section>
        </div>
      )}

      <BanConfirmModal
        pending={pendingAction}
        currentUserId={currentUser?.id}
        onClose={() => setPendingAction(null)}
        onSuccess={updateUserStatus}
      />
    </section>
  )
}

function AdminReports() {
  const [reports, setReports] = useState<AdminReport[]>([])
  const [status, setStatus] = useState<NonNullable<AdminReport['status']>>('PENDING')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [resolvingId, setResolvingId] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await getAdminReports(status, 0, 20)
      setReports(response.data.content ?? [])
    } catch (error: any) {
      setError(adminErrorMessage(error, 'Could not load reports.'))
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    void load()
  }, [load])

  const resolve = async (report: AdminReport, resolution: NonNullable<AdminReport['status']>) => {
    if (!report.id) return
    setResolvingId(report.id)
    try {
      const response = await resolveAdminReport(report.id, resolution, `Resolved from admin console as ${resolution}.`)
      setReports(prev => prev.map(item => item.id === report.id ? response.data : item).filter(item => item.status === status))
      toastSuccess('Report updated successfully.')
    } catch (error: any) {
      toastError(adminErrorMessage(error, 'Could not update report.'))
    } finally {
      setResolvingId('')
    }
  }

  return (
    <section className="admin-page" aria-labelledby="admin-reports-title">
      <AdminPageHeader
        eyebrow="Moderation"
        title="Reports"
        description="Review real moderation reports from the backend. Unsupported audit-log workflows are intentionally not shown as working tools."
      />

      {error && <AdminAlert message={error} onRetry={load} />}

      <div className="admin-toolbar">
        <label className="admin-select-field">
          <span>Report status</span>
          <select value={status} onChange={event => setStatus(event.target.value as NonNullable<AdminReport['status']>)}>
            <option value="PENDING">Pending</option>
            <option value="REVIEWED">Reviewed</option>
            <option value="DISMISSED">Dismissed</option>
            <option value="ACTION_TAKEN">Action taken</option>
          </select>
        </label>
      </div>

      <div className="admin-table-card" aria-busy={loading}>
        {loading ? <AdminSkeletonRows count={6} /> : (
          <>
            {reports.length === 0 ? (
              <AdminEmpty title="No reports in this status" body="The backend returned no moderation reports for this filter." />
            ) : (
              <div className="admin-report-list">
                {reports.map(report => (
                  <article key={report.id} className="admin-report-card">
                    <div>
                      <span className="admin-classification">REAL</span>
                      <h2>{report.targetType || 'Unknown target'} report</h2>
                      <p>{report.details || 'No additional report details were provided.'}</p>
                    </div>
                    <dl className="admin-report-meta">
                      <div><dt>Reason</dt><dd>{formatEnum(report.reason)}</dd></div>
                      <div><dt>Status</dt><dd>{formatEnum(report.status)}</dd></div>
                      <div><dt>Reporter</dt><dd>{report.reporterId || 'Unknown'}</dd></div>
                      <div><dt>Target</dt><dd>{report.targetId || 'Unknown'}</dd></div>
                      <div><dt>Created</dt><dd>{formatDate(report.createdAt)}</dd></div>
                    </dl>
                    {report.status === 'PENDING' && (
                      <div className="admin-report-actions">
                        <button type="button" className="admin-secondary-button" disabled={resolvingId === report.id} onClick={() => resolve(report, 'DISMISSED')}>
                          {resolvingId === report.id ? <ButtonLoader label="Updating..." /> : 'Dismiss'}
                        </button>
                        <button type="button" className="admin-primary-button" disabled={resolvingId === report.id} onClick={() => resolve(report, 'ACTION_TAKEN')}>
                          {resolvingId === report.id ? <ButtonLoader label="Updating..." /> : 'Mark action taken'}
                        </button>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}

function BanConfirmModal({
  pending,
  currentUserId,
  onClose,
  onSuccess,
}: {
  pending: { action: Action; user: AdminUser } | null
  currentUserId?: string
  onClose: () => void
  onSuccess: (userId: string, banned: boolean) => void
}) {
  const [submitting, setSubmitting] = useState(false)
  const isBan = pending?.action === 'ban'
  const isSelf = pending?.user.id === currentUserId

  const submit = async () => {
    if (!pending || isSelf) return
    setSubmitting(true)
    try {
      const response = isBan ? await banAdminUser(pending.user.id) : await unbanAdminUser(pending.user.id)
      onSuccess(response.data.userId, response.data.banned)
      toastSuccess(isBan ? 'User banned successfully.' : 'User unbanned successfully.')
      onClose()
    } catch (error: any) {
      toastError(adminErrorMessage(error, isBan ? 'Could not ban user. Please try again.' : 'Could not unban user. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={Boolean(pending)} onClose={submitting ? () => undefined : onClose} title={isBan ? 'Ban user?' : 'Unban user?'} size="md">
      {pending && (
        <div className="admin-confirm">
          <p>
            {isBan
              ? 'This will prevent the user from creating projects, publishing ships, uploading images, liking, commenting, and following builders. They can still browse public content and log out.'
              : "This will restore the user's ability to create projects, publish ships, upload images, like, comment, and follow builders."}
          </p>
          <div className="admin-confirm-target">
            {pending.user.avatarUrl ? <img src={pending.user.avatarUrl} alt="" /> : <Icon name="user" size={22} />}
            <div>
              <strong>{pending.user.username}</strong>
              <span>Status: {pending.user.banned ? 'Banned' : 'Active'}</span>
            </div>
          </div>
          {isSelf && <p className="admin-inline-warning" role="alert">Admins cannot ban or unban their own account.</p>}
          <div className="admin-confirm-actions">
            <button type="button" className="admin-secondary-button" disabled={submitting} onClick={onClose}>Cancel</button>
            <button
              type="button"
              className={isBan ? 'admin-danger-button' : 'admin-primary-button'}
              disabled={submitting || isSelf}
              onClick={submit}
            >
              {submitting ? <ButtonLoader label={isBan ? 'Banning...' : 'Unbanning...'} /> : isBan ? 'Ban user' : 'Unban user'}
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function UserTableRow({
  user,
  currentUserId,
  onAction,
}: {
  user: AdminUser
  currentUserId?: string
  onAction: (action: Action) => void
}) {
  const isSelf = user.id === currentUserId
  return (
    <div className="admin-user-row" role="row">
      <span role="cell" className="admin-user-cell">
        {user.avatarUrl ? <img src={user.avatarUrl} alt={`${user.username} avatar`} /> : <span><Icon name="user" size={18} /></span>}
        <span>
          <strong>{user.username}</strong>
          <small>{user.email || user.githubId || user.id}</small>
        </span>
      </span>
      <span role="cell"><RoleBadge admin={hasAdminRole(user)} /></span>
      <span role="cell"><StatusBadge banned={Boolean(user.banned)} /></span>
      <span role="cell">{user.projectCount ?? 0}</span>
      <span role="cell">{user.shipScore ?? 0}</span>
      <span role="cell">{formatDate(user.createdAt)}</span>
      <span role="cell" className="admin-row-actions">
        <Link to={`/admin/users/${user.id}`}>View</Link>
        {!user.banned && <button type="button" disabled={isSelf} onClick={() => onAction('ban')}>Ban</button>}
        {user.banned && <button type="button" disabled={isSelf} onClick={() => onAction('unban')}>Unban</button>}
      </span>
    </div>
  )
}

function UserMiniRow({ user }: { user: AdminUser }) {
  return (
    <Link className="admin-mini-row" to={`/admin/users/${user.id}`}>
      {user.avatarUrl ? <img src={user.avatarUrl} alt="" /> : <span><Icon name="user" size={18} /></span>}
      <span>
        <strong>{user.username}</strong>
        <small className="admin-mini-badges">
          <StatusBadge banned={Boolean(user.banned)} />
          <RoleBadge admin={hasAdminRole(user)} />
        </small>
      </span>
      <Icon name="chevronRight" size={16} />
    </Link>
  )
}

function ReportMiniRow({ report }: { report: AdminReport }) {
  return (
    <Link className="admin-mini-row" to="/admin/reports">
      <span><Icon name="alert" size={18} /></span>
      <span>
        <strong>{formatEnum(report.reason) || 'Report'}</strong>
        <small>{report.targetType || 'Target'} · {formatDate(report.createdAt)}</small>
      </span>
      <Icon name="chevronRight" size={16} />
    </Link>
  )
}

function AdminPageHeader({
  eyebrow,
  title,
  description,
  meta,
  action,
}: {
  eyebrow: string
  title: string
  description: string
  meta?: string
  action?: ReactNode
}) {
  return (
    <div className="admin-page-header">
      <div>
        <p className="admin-eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{description}</p>
        {meta && <span className="admin-page-meta">{meta}</span>}
      </div>
      {action && <div>{action}</div>}
    </div>
  )
}

function AdminSafetyNote() {
  return (
    <div className="admin-safety-note" role="status">
      <Icon name="alert" size={18} />
      <span>Admin actions affect real user access. Review the target account before confirming ban or unban changes.</span>
    </div>
  )
}

function MetricCard({ label, value, loading, classification }: { label: string; value?: number; loading: boolean; classification: 'REAL' | 'DERIVED' }) {
  return (
    <article className="admin-metric-card">
      <span className="admin-classification">{classification}</span>
      <strong>{loading ? <Skeleton style={{ width: 64, height: 34 }} /> : value ?? 0}</strong>
      <p>{label}</p>
    </article>
  )
}

function AdminPanel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="admin-panel">
      <header>
        <h2>{title}</h2>
        {action}
      </header>
      {children}
    </section>
  )
}

function AdminAlert({ message, onRetry }: { message: string; onRetry: () => void | Promise<void> }) {
  return (
    <div className="admin-alert" role="alert">
      <Icon name="alert" size={18} />
      <span>{message}</span>
      <button type="button" onClick={() => void onRetry()}>Retry</button>
    </div>
  )
}

function AdminEmpty({ title, body, action }: { title: string; body: string; action?: ReactNode }) {
  return (
    <div className="admin-empty" role="status">
      <Icon name="info" size={22} />
      <h2>{title}</h2>
      <p>{body}</p>
      {action && <div className="admin-empty-action">{action}</div>}
    </div>
  )
}

function AdminSkeletonRows({ count }: { count: number }) {
  return (
    <div className="admin-skeleton-list" aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="admin-skeleton-row">
          <Skeleton style={{ width: 44, height: 44, borderRadius: 12 }} />
          <Skeleton style={{ width: '70%', height: 18 }} />
          <Skeleton style={{ width: '42%', height: 14 }} />
        </div>
      ))}
    </div>
  )
}

function AdminPagination({ page, onPrev, onNext }: { page: PageResponse<AdminUser> | null; onPrev: () => void; onNext: () => void }) {
  if (!page) return null
  return (
    <div className="admin-pagination" aria-label="User pagination">
      <button type="button" disabled={page.number <= 0} onClick={onPrev}>Previous</button>
      <span>Page {page.number + 1} of {Math.max(page.totalPages || 1, 1)}</span>
      <button type="button" disabled={Boolean(page.last) || page.number + 1 >= page.totalPages} onClick={onNext}>Next</button>
    </div>
  )
}

function StatusBadge({ banned }: { banned: boolean }) {
  return <span className={`admin-status-badge ${banned ? 'admin-status-banned' : 'admin-status-active'}`}>{banned ? 'Banned' : 'Active'}</span>
}

function RoleBadge({ admin }: { admin: boolean }) {
  return <span className={`admin-role-badge ${admin ? 'admin-role-admin' : ''}`}>{admin ? 'Admin' : 'User'}</span>
}

function hasAdminRole(user?: Pick<AdminUser, 'roles'> | null) {
  return Boolean(user?.roles?.includes('ADMIN') || user?.roles?.includes('ROLE_ADMIN'))
}

function adminErrorMessage(error: any, fallback = 'The admin request failed. Please try again.') {
  const status = error?.response?.status
  if (status === 401) return 'Admin access requires sign in.'
  if (status === 403) return error?.response?.data?.message || 'You do not have permission to perform this action.'
  if (status === 404) return 'The requested admin record was not found.'
  return error?.response?.data?.message || fallback
}

function formatDate(value?: string) {
  if (!value) return 'Not available'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Not available'
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

function formatEnum(value?: string) {
  if (!value) return ''
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, char => char.toUpperCase())
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

function relativeTime(value: string) {
  const date = new Date(value)
  const diffMs = Date.now() - date.getTime()
  if (Number.isNaN(diffMs) || diffMs < 30_000) return 'just now'
  const minutes = Math.floor(diffMs / 60_000)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return formatDate(value)
}
