import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Spinner } from '../components/Spinner'
import { useToast } from '../components/Toast'
import { getMyProjects, createProject, deleteProject } from '../api/endpoints'

interface Project {
  id: string; name: string; description: string
  techTags: string[]; shipCount: number; lastShipAt?: string
}

function CreateProjectModal({ onClose, onCreate }: { onClose: () => void; onCreate: (p: Project) => void }) {
  const { toast } = useToast()
  const [name, setName] = useState('')
  const [desc, setDesc] = useState('')
  const [tags, setTags] = useState('')
  const [saving, setSaving] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim() || !desc.trim()) return
    setSaving(true)
    try {
      const r = await createProject({
        name: name.trim(),
        description: desc.trim(),
        techTags: tags.split(',').map(t => t.trim()).filter(Boolean),
      })
      onCreate(r.data)
      toast('Project created', 'success')
      onClose()
    } catch {
      toast('Could not create project', 'error')
    } finally { setSaving(false) }
  }

  return (
    <div className="modal-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal-panel" style={{ padding: 'var(--space-6)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-5)' }}>
          <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 700 }}>New Project</h2>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--color-text-muted)', fontSize: 20 }}>✕</button>
        </div>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <label style={labelStyle}>
            Project Name *
            <input value={name} onChange={e => setName(e.target.value)} maxLength={100} required
              placeholder="My Awesome Project" style={inputStyle} />
            <span style={counterStyle}>{name.length}/100</span>
          </label>
          <label style={labelStyle}>
            Description *
            <textarea value={desc} onChange={e => setDesc(e.target.value)} maxLength={2000} required
              rows={4} placeholder="What are you building?" style={{ ...inputStyle, resize: 'vertical' }} />
            <span style={counterStyle}>{desc.length}/2000</span>
          </label>
          <label style={labelStyle}>
            Tech Tags <span style={{ color: 'var(--color-text-muted)', fontWeight: 400 }}>(comma-separated)</span>
            <input value={tags} onChange={e => setTags(e.target.value)}
              placeholder="React, Node.js, MongoDB" style={inputStyle} />
          </label>
          <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
            <button type="button" onClick={onClose} style={ghostBtnStyle}>Cancel</button>
            <button type="submit" disabled={saving || !name.trim() || !desc.trim()} style={primaryBtnStyle}>
              {saving ? 'Creating…' : 'Create Project'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const { toast } = useToast()

  useEffect(() => {
    getMyProjects()
      .then(r => setProjects(r.data.content ?? r.data ?? []))
      .catch(() => toast('Could not load projects', 'error'))
      .finally(() => setLoading(false))
  }, [])

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Delete "${name}"? This will remove all ships. Cannot be undone.`)) return
    try {
      await deleteProject(id)
      setProjects(p => p.filter(x => x.id !== id))
      toast('Project deleted', 'success')
    } catch { toast('Could not delete project', 'error') }
  }

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-5)' }}>
        <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700 }}>My Projects</h1>
        <button onClick={() => setShowCreate(true)} style={primaryBtnStyle}>+ New Project</button>
      </div>

      {loading && <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}><Spinner size={28} /></div>}

      {!loading && projects.length === 0 && (
        <div style={{ textAlign: 'center', padding: 'var(--space-12)', color: 'var(--color-text-muted)' }}>
          <div style={{ fontSize: 40, marginBottom: 'var(--space-4)' }}>📦</div>
          <div style={{ fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 'var(--space-2)' }}>No projects yet</div>
          <div style={{ fontSize: 'var(--text-sm)', marginBottom: 'var(--space-5)' }}>Create your first project to start shipping.</div>
          <button onClick={() => setShowCreate(true)} style={primaryBtnStyle}>Create Project</button>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        {projects.map(p => (
          <div key={p.id} style={{
            background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-5)',
            transition: 'background var(--transition-fast)',
          }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--color-surface-2)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'var(--color-surface)')}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 'var(--space-3)' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <Link to={`/projects/${p.id}`} style={{
                  fontSize: 'var(--text-base)', fontWeight: 600,
                  color: 'var(--color-text-primary)',
                  transition: 'color var(--transition-fast)',
                }}
                  onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-brand-dim)')}
                  onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-primary)')}
                >{p.name}</Link>
                <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', marginTop: 4, lineHeight: 1.5 }}>
                  {p.description.length > 120 ? p.description.slice(0, 120) + '…' : p.description}
                </p>
                {p.techTags?.length > 0 && (
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 'var(--space-3)' }}>
                    {p.techTags.slice(0, 5).map(t => (
                      <span key={t} style={{
                        fontSize: 'var(--text-xs)', padding: '2px 8px',
                        background: 'var(--color-brand-light)', color: 'var(--color-brand-dim)',
                        borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-mono)',
                      }}>{t}</span>
                    ))}
                  </div>
                )}
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 'var(--space-3)', fontFamily: 'var(--font-mono)' }}>
                  {p.shipCount ?? 0} ships
                </div>
              </div>
              <button onClick={() => handleDelete(p.id, p.name)} style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: 'var(--color-text-muted)', fontSize: 'var(--text-xs)',
                padding: '4px 8px', borderRadius: 'var(--radius-sm)',
                fontFamily: 'inherit', flexShrink: 0,
                transition: 'color var(--transition-fast)',
              }}
                onMouseEnter={e => (e.currentTarget.style.color = 'var(--color-error)')}
                onMouseLeave={e => (e.currentTarget.style.color = 'var(--color-text-muted)')}
              >Delete</button>
            </div>
          </div>
        ))}
      </div>

      {showCreate && (
        <CreateProjectModal
          onClose={() => setShowCreate(false)}
          onCreate={p => setProjects(prev => [p, ...prev])}
        />
      )}
    </div>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: 6,
  fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--color-text-secondary)',
  position: 'relative',
}
const inputStyle: React.CSSProperties = {
  background: 'var(--color-surface-2)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-md)',
  padding: 'var(--space-3)',
  color: 'var(--color-text-primary)',
  fontSize: 'var(--text-sm)',
  outline: 'none',
  width: '100%',
}
const counterStyle: React.CSSProperties = {
  fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)',
  textAlign: 'right', fontFamily: 'var(--font-mono)',
}
const ghostBtnStyle: React.CSSProperties = {
  padding: '8px 18px', borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)', background: 'transparent',
  color: 'var(--color-text-secondary)', cursor: 'pointer',
  fontSize: 'var(--text-sm)', fontWeight: 500, fontFamily: 'inherit',
}
const primaryBtnStyle: React.CSSProperties = {
  padding: '8px 18px', borderRadius: 'var(--radius-md)', border: 'none',
  background: 'linear-gradient(135deg, var(--color-brand), var(--color-brand-dim))',
  color: '#fff', cursor: 'pointer', fontSize: 'var(--text-sm)',
  fontWeight: 600, fontFamily: 'inherit',
}
