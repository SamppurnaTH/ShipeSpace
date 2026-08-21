import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getMyProjects } from '../api/endpoints'
import { Badge } from '../components/Badge'
import { ProjectModal } from '../components/ProjectModal'
import { ProjectCardSkeleton } from '../components/common/Loading'
import { Icon } from '../components/Icon'

interface Project {
  id: string
  name: string
  description: string
  techTags?: string[]
  shipCount?: number
  lastShipAt?: string
  createdAt: string
}

export function ProjectListPage() {
  const { user } = useAuth()
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const isBanned = Boolean(user?.banned)

  const fetchProjects = () => {
    setLoading(true)
    setError('')
    getMyProjects()
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : res.data.content ?? []
        setProjects(list)
      })
      .catch(() => {
        setProjects([])
        setError('Could not load your projects. Try again in a moment.')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { fetchProjects() }, [])

  const timeAgo = (iso: string) => {
    if (!iso) return ''
    const diff = Date.now() - new Date(iso).getTime()
    const d = Math.floor(diff / 86400000)
    if (d < 1) return 'today'
    if (d === 1) return 'yesterday'
    return `${d}d ago`
  }

  if (loading) {
    return (
      <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }} aria-busy="true">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-6)' }}>
          <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, margin: 0 }}>My Projects</h1>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {Array.from({ length: 4 }).map((_, i) => <ProjectCardSkeleton key={i} />)}
        </div>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: 'var(--space-6)',
      }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, margin: 0 }}>My Projects</h1>
        <button disabled={isBanned} onClick={() => !isBanned && setShowCreate(true)} style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '8px 18px', borderRadius: 'var(--radius-md)',
          border: 'none', background: isBanned ? 'var(--color-surface-2)' : 'var(--color-brand)', color: isBanned ? 'var(--color-text-muted)' : '#fff',
          fontSize: 'var(--text-sm)', fontWeight: 600,
          cursor: isBanned ? 'not-allowed' : 'pointer', fontFamily: 'inherit',
          transition: 'all var(--transition-fast)',
          boxShadow: '0 2px 8px rgba(99,102,241,0.25)',
        }}
          onMouseEnter={e => { if (!isBanned) e.currentTarget.style.background = 'var(--color-brand-hover)' }}
          onMouseLeave={e => { if (!isBanned) e.currentTarget.style.background = 'var(--color-brand)' }}
        >
          {isBanned ? 'Writes blocked' : 'New Project'}
        </button>
      </div>

      {isBanned && (
        <div className="blocked-write-banner" role="status">
          Project creation is unavailable for this account. Public browsing and existing read pages remain available.
        </div>
      )}

      {error && (
        <div role="alert" style={{
          textAlign: 'center', padding: 'var(--space-8)',
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          marginBottom: 'var(--space-4)',
          color: 'var(--color-text-secondary)',
        }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-3)' }}><Icon name="alert" size={28} /></div>
          <div style={{ fontWeight: 700, marginBottom: 'var(--space-2)', color: 'var(--color-text-primary)' }}>
            Projects did not load
          </div>
          <div style={{ fontSize: 'var(--text-sm)', marginBottom: 'var(--space-4)' }}>{error}</div>
          <button onClick={fetchProjects} style={{
            padding: '9px 20px',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--color-border)',
            background: 'var(--color-surface)',
            color: 'var(--color-text-primary)',
            cursor: 'pointer',
            fontWeight: 600,
            fontFamily: 'inherit',
          }}>
            Retry
          </button>
        </div>
      )}

      {/* Empty state */}
      {!error && projects.length === 0 && (
        <div style={{
          textAlign: 'center', padding: 'var(--space-10)',
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
        }}>
          <div style={{ display: 'inline-flex', marginBottom: 'var(--space-4)' }}><Icon name="box" size={48} /></div>
          <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600, marginBottom: 'var(--space-2)', color: 'var(--color-text-primary)' }}>
            No projects yet
          </div>
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', marginBottom: 'var(--space-5)' }}>
            Start by creating your first project.
          </div>
          <button disabled={isBanned} onClick={() => !isBanned && setShowCreate(true)} style={{
            padding: '10px 24px', borderRadius: 'var(--radius-md)',
            border: 'none', background: isBanned ? 'var(--color-surface-2)' : 'var(--color-brand)', color: isBanned ? 'var(--color-text-muted)' : '#fff',
            fontSize: 'var(--text-sm)', fontWeight: 600,
            cursor: isBanned ? 'not-allowed' : 'pointer', fontFamily: 'inherit',
          }}>
            {isBanned ? 'Writes blocked' : 'Create Project'}
          </button>
        </div>
      )}

      {/* Project list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        {projects.map(project => (
          <Link
            key={project.id}
            to={`/projects/${project.id}`}
            style={{
              display: 'block', textDecoration: 'none',
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-5)',
              transition: 'all var(--transition-fast)',
            }}
            onMouseEnter={e => {
              e.currentTarget.style.boxShadow = 'var(--shadow-md)'
              e.currentTarget.style.borderColor = 'var(--color-brand)'
            }}
            onMouseLeave={e => {
              e.currentTarget.style.boxShadow = 'none'
              e.currentTarget.style.borderColor = 'var(--color-border)'
            }}
          >
            <div style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: 'var(--space-2)' }}>
              {project.name}
            </div>
            {project.description && (
              <p style={{
                fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)',
                margin: '0 0 var(--space-3) 0', lineHeight: 1.5,
                overflow: 'hidden', textOverflow: 'ellipsis',
                display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
              }}>
                {project.description}
              </p>
            )}
            {project.techTags && project.techTags.length > 0 && (
              <div style={{ display: 'flex', gap: 'var(--space-1)', flexWrap: 'wrap', marginBottom: 'var(--space-3)' }}>
                {project.techTags.slice(0, 5).map(t => (
                  <Badge key={t} variant="info">{t}</Badge>
                ))}
              </div>
            )}
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              {project.shipCount ?? 0} ships
              {project.lastShipAt && <> · Last ship {timeAgo(project.lastShipAt)}</>}
            </div>
          </Link>
        ))}
      </div>

      {/* Create project modal */}
      <ProjectModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSuccess={fetchProjects}
      />
    </div>
  )
}
