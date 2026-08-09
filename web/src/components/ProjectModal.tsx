import { useState, useEffect } from 'react'
import { Modal } from './Modal'
import { createProject, updateProject } from '../api/endpoints'
import { toastSuccess, toastError } from './Toast'
import { ButtonLoader } from './common/Loading'
import { Icon } from './Icon'

interface ProjectModalProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  editProject?: { id: string; name: string; description: string; techTags?: string[]; externalLinks?: { url: string; type: string }[] } | null
}

export function ProjectModal({ open, onClose, onSuccess, editProject }: ProjectModalProps) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [tagInput, setTagInput] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [links, setLinks] = useState<{ url: string; type: string }[]>([])
  const [submitting, setSubmitting] = useState(false)

  const isEdit = !!editProject

  useEffect(() => {
    if (open && editProject) {
      setName(editProject.name)
      setDescription(editProject.description)
      setTags(editProject.techTags ?? [])
      setLinks(editProject.externalLinks ?? [])
    }
    if (open && !editProject) {
      setName('')
      setDescription('')
      setTags([])
      setLinks([])
      setTagInput('')
    }
  }, [open, editProject])

  const addTag = () => {
    const t = tagInput.trim()
    if (t && !tags.includes(t)) {
      setTags(prev => [...prev, t])
      setTagInput('')
    }
  }

  const removeTag = (tag: string) => setTags(prev => prev.filter(t => t !== tag))

  const addLink = () => setLinks(prev => [...prev, { url: '', type: '' }])
  const removeLink = (i: number) => setLinks(prev => prev.filter((_, idx) => idx !== i))
  const updateLink = (i: number, field: 'url' | 'type', value: string) => {
    setLinks(prev => prev.map((l, idx) => idx === i ? { ...l, [field]: value } : l))
  }

  const handleSubmit = async () => {
    if (!name.trim() || !description.trim()) return
    setSubmitting(true)
    try {
      const validLinks = links.filter(l => l.url.trim())
      const data = {
        name: name.trim(),
        description: description.trim(),
        techTags: tags.length > 0 ? tags : undefined,
        externalLinks: validLinks.length > 0 ? validLinks : undefined,
      }
      if (isEdit) {
        await updateProject(editProject!.id, data)
        toastSuccess('Project updated')
      } else {
        await createProject(data)
        toastSuccess('Project created')
      }
      onSuccess()
      onClose()
    } catch {
      toastError(`Failed to ${isEdit ? 'update' : 'create'} project`)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Edit Project' : 'New Project'} size="md">
      {/* Name */}
      <div style={{ marginBottom: 'var(--space-5)' }}>
        <label style={labelStyle}>Project Name *</label>
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="My Awesome Project"
          maxLength={100}
          style={inputStyle}
        />
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', textAlign: 'right', marginTop: 4 }}>
          {name.length}/100
        </div>
      </div>

      {/* Description */}
      <div style={{ marginBottom: 'var(--space-5)' }}>
        <label style={labelStyle}>Description *</label>
        <textarea
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="What are you building?"
          maxLength={2000}
          rows={4}
          style={{ ...inputStyle, resize: 'vertical' }}
        />
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', textAlign: 'right', marginTop: 4 }}>
          {description.length}/2000
        </div>
      </div>

      {/* Tech tags */}
      <div style={{ marginBottom: 'var(--space-5)' }}>
        <label style={labelStyle}>Tech Tags</label>
        <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', marginBottom: 'var(--space-2)' }}>
          {tags.map(tag => (
            <span key={tag} style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '3px 10px', borderRadius: 'var(--radius-full)',
              background: 'var(--color-info-bg)',
              border: '1px solid var(--color-info)',
              fontSize: 'var(--text-xs)', color: 'var(--color-info)',
              fontWeight: 600,
            }}>
              {tag}
              <button onClick={() => removeTag(tag)} style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: 'var(--color-info)', fontSize: 12, padding: 0,
                lineHeight: 1,
              }} aria-label={`Remove ${tag}`}><Icon name="close" size={12} /></button>
            </span>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <input
            value={tagInput}
            onChange={e => setTagInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag() } }}
            placeholder="Add tag..."
            style={{ ...inputStyle, flex: 1 }}
          />
          <button onClick={addTag} style={{
            padding: '8px 14px', borderRadius: 'var(--radius-md)',
            border: '1px solid var(--color-border)', background: 'var(--color-surface-2)',
            cursor: 'pointer', fontSize: 'var(--text-sm)', fontFamily: 'inherit',
            color: 'var(--color-text-secondary)',
          }}>Add</button>
        </div>
      </div>

      {/* External links */}
      <div style={{ marginBottom: 'var(--space-6)' }}>
        <label style={labelStyle}>External Links <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(optional)</span></label>
        {links.map((link, i) => (
          <div key={i} style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
            <input value={link.url} onChange={e => updateLink(i, 'url', e.target.value)}
              placeholder="https://..." style={{ ...inputStyle, flex: 2 }} />
            <select value={link.type} onChange={e => updateLink(i, 'type', e.target.value)}
              style={{ ...inputStyle, flex: 1 }}>
              <option value="">Type</option>
              <option value="github">GitHub</option>
              <option value="website">Website</option>
              <option value="demo">Demo</option>
            </select>
            <button onClick={() => removeLink(i)} style={{
              background: 'none', border: 'none', cursor: 'pointer',
              color: 'var(--color-text-muted)', fontSize: 16,
            }} aria-label="Remove link"><Icon name="close" size={14} /></button>
          </div>
        ))}
        <button onClick={addLink} style={{
          background: 'none', border: '1px dashed var(--color-border)',
          borderRadius: 'var(--radius-md)', padding: '6px 12px',
          fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)',
          cursor: 'pointer', fontFamily: 'inherit', width: '100%',
        }}>+ Add link</button>
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'flex-end' }}>
        <button onClick={onClose} style={{
          padding: '8px 20px', borderRadius: 'var(--radius-md)',
          border: '1px solid var(--color-border)', background: 'var(--color-surface)',
          cursor: 'pointer', fontSize: 'var(--text-sm)', fontFamily: 'inherit',
          color: 'var(--color-text-secondary)',
        }}>Cancel</button>
        <button
          onClick={handleSubmit}
          disabled={!name.trim() || !description.trim() || submitting}
          style={{
            padding: '8px 24px', borderRadius: 'var(--radius-md)',
            border: 'none',
            background: name.trim() && description.trim() ? 'var(--color-brand)' : 'var(--color-surface-2)',
            color: name.trim() && description.trim() ? '#fff' : 'var(--color-text-muted)',
            cursor: name.trim() && description.trim() ? 'pointer' : 'default',
            fontSize: 'var(--text-sm)', fontWeight: 600, fontFamily: 'inherit',
          }}
        >
          {submitting ? <ButtonLoader label={isEdit ? 'Saving...' : 'Creating...'} /> : (isEdit ? 'Save Changes' : 'Create Project')}
        </button>
      </div>
    </Modal>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: 'var(--text-sm)', fontWeight: 600,
  color: 'var(--color-text-primary)', marginBottom: 'var(--space-2)',
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '8px 12px',
  borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)',
  background: 'var(--color-surface)',
  fontSize: 'var(--text-sm)', fontFamily: 'inherit',
  color: 'var(--color-text-primary)', outline: 'none',
}
