import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Modal } from './Modal'
import { createShip, uploadShipImages, getMyProjects } from '../api/endpoints'
import { toastSuccess, toastError } from './Toast'
import { ButtonLoader, LoadingSpinner, UploadProgress } from './common/Loading'
import { Icon } from './Icon'

interface CreateShipModalProps {
  open: boolean
  onClose: () => void
}

interface Project {
  id: string
  name: string
}

type UploadStatus = 'idle' | 'loading' | 'success' | 'error'

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const SUPPORTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])

export function CreateShipModal({ open, onClose }: CreateShipModalProps) {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState('')
  const [content, setContent] = useState('')
  const [links, setLinks] = useState<{ url: string; type: string }[]>([])
  const [images, setImages] = useState<File[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [loadingProjects, setLoadingProjects] = useState(true)
  const [uploadStatus, setUploadStatus] = useState<UploadStatus>('idle')
  const [uploadMessage, setUploadMessage] = useState('')

  useEffect(() => {
    if (!open) return
    setLoadingProjects(true)
    getMyProjects()
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : res.data.content ?? []
        setProjects(list)
        if (list.length === 1) setSelectedProjectId(list[0].id)
      })
      .catch(() => {})
      .finally(() => setLoadingProjects(false))
  }, [open])

  const addLink = () => setLinks(prev => [...prev, { url: '', type: '' }])
  const removeLink = (i: number) => setLinks(prev => prev.filter((_, idx) => idx !== i))
  const updateLink = (i: number, field: 'url' | 'type', value: string) => {
    setLinks(prev => prev.map((l, idx) => idx === i ? { ...l, [field]: value } : l))
  }

  const handleSubmit = async () => {
    if (!selectedProjectId || !content.trim()) return
    setSubmitting(true)
    setUploadStatus('idle')
    setUploadMessage('')
    try {
      const validLinks = links.filter(l => l.url.trim())
      const res = await createShip({
        projectId: selectedProjectId,
        content: content.trim(),
        externalLinks: validLinks.length > 0 ? validLinks : undefined,
      })
      const shipId = res.data.id

      // Upload images if any
      if (images.length > 0 && shipId) {
        const formData = new FormData()
        images.forEach(f => formData.append('images', f))
        setUploadStatus('loading')
        setUploadMessage(`Uploading ${images.length} image${images.length === 1 ? '' : 's'}...`)
        try {
          await uploadShipImages(shipId, formData)
          setUploadStatus('success')
          setUploadMessage('Image upload complete.')
        } catch {
          setUploadStatus('error')
          setUploadMessage('Ship created, but images could not be uploaded.')
          toastError('Ship created, but image upload failed')
        }
      }

      toastSuccess('Ship published')
      onClose()
      setContent('')
      setLinks([])
      setImages([])
      setUploadStatus('idle')
      setUploadMessage('')
      setSelectedProjectId('')
      navigate(`/ships/${shipId}`)
    } catch {
      toastError('Failed to create ship')
    } finally {
      setSubmitting(false)
    }
  }

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const selected = Array.from(e.target.files)
      const valid: File[] = []

      for (const file of selected) {
        if (!SUPPORTED_IMAGE_TYPES.has(file.type)) {
          setUploadStatus('error')
          setUploadMessage('Use JPG, PNG, WebP, or GIF images only.')
          continue
        }
        if (file.size > MAX_IMAGE_BYTES) {
          setUploadStatus('error')
          setUploadMessage('Each image must be 10 MB or smaller.')
          continue
        }
        valid.push(file)
      }

      if (valid.length > 0) {
        setUploadStatus('idle')
        setUploadMessage('')
        setImages(prev => [...prev, ...valid])
      }
      e.currentTarget.value = ''
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New Ship" size="md">
      {/* Project selector */}
      <div style={{ marginBottom: 'var(--space-5)' }}>
        <label style={labelStyle}>Project *</label>
        {loadingProjects ? (
          <div className="inline-loading" aria-live="polite" aria-busy="true">
            <LoadingSpinner size={16} label="Loading projects" />
            <span>Loading projects...</span>
          </div>
        ) : projects.length === 0 ? (
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-warning)' }}>
            No projects yet. Create a project first.
          </div>
        ) : (
          <select
            value={selectedProjectId}
            onChange={e => setSelectedProjectId(e.target.value)}
            style={inputStyle}
          >
            <option value="">Select a project...</option>
            {projects.map(p => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        )}
      </div>

      {/* Content */}
      <div style={{ marginBottom: 'var(--space-5)' }}>
        <label style={labelStyle}>What did you ship? *</label>
        <textarea
          value={content}
          onChange={e => setContent(e.target.value)}
          placeholder="Share what you've been building..."
          maxLength={5000}
          rows={6}
          style={{ ...inputStyle, resize: 'vertical', minHeight: 120 }}
        />
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', textAlign: 'right', marginTop: 4 }}>
          {content.length} / 5000
        </div>
      </div>

      {/* External links */}
      <div style={{ marginBottom: 'var(--space-5)' }}>
        <label style={labelStyle}>External Links <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(optional)</span></label>
        {links.map((link, i) => (
          <div key={i} style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
            <input
              value={link.url}
              onChange={e => updateLink(i, 'url', e.target.value)}
              placeholder="https://..."
              style={{ ...inputStyle, flex: 2 }}
            />
            <select value={link.type} onChange={e => updateLink(i, 'type', e.target.value)}
              style={{ ...inputStyle, flex: 1 }}>
              <option value="">Type</option>
              <option value="github">GitHub</option>
              <option value="website">Website</option>
              <option value="demo">Demo</option>
              <option value="other">Other</option>
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
          cursor: 'pointer', fontFamily: 'inherit',
          width: '100%', transition: 'all var(--transition-fast)',
        }}
          onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--color-brand)'}
          onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--color-border)'}
        >
          + Add link
        </button>
      </div>

      {/* Images */}
      <div style={{ marginBottom: 'var(--space-6)' }}>
        <label style={labelStyle}>Images <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(optional)</span></label>
        <label style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          padding: 'var(--space-4)',
          border: '1px dashed var(--color-border)',
          borderRadius: 'var(--radius-md)',
          cursor: 'pointer', fontSize: 'var(--text-sm)',
          color: 'var(--color-text-secondary)',
          transition: 'all var(--transition-fast)',
        }}>
          <Icon name="upload" size={16} /> Attach images
          <input type="file" accept="image/*" multiple onChange={handleImageSelect} style={{ display: 'none' }} />
        </label>
        {images.length > 0 && (
          <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)', flexWrap: 'wrap' }}>
            {images.map((img, i) => (
              <div key={i} style={{
                position: 'relative', width: 60, height: 60, borderRadius: 'var(--radius-sm)',
                overflow: 'hidden', border: '1px solid var(--color-border)',
              }}>
                <img src={URL.createObjectURL(img)} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                <button onClick={() => setImages(prev => prev.filter((_, idx) => idx !== i))}
                  style={{
                    position: 'absolute', top: 2, right: 2,
                    width: 18, height: 18, borderRadius: '50%',
                    background: 'rgba(0,0,0,0.6)', border: 'none',
                    color: '#fff', fontSize: 10, cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }} aria-label="Remove image"><Icon name="close" size={12} /></button>
              </div>
            ))}
          </div>
        )}
        {uploadStatus !== 'idle' && uploadMessage && (
          <UploadProgress label={uploadMessage} tone={uploadStatus} />
        )}
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'flex-end' }}>
        <button onClick={onClose} style={{
          padding: '8px 20px', borderRadius: 'var(--radius-md)',
          border: '1px solid var(--color-border)', background: 'var(--color-surface)',
          cursor: 'pointer', fontSize: 'var(--text-sm)', fontFamily: 'inherit',
          color: 'var(--color-text-secondary)', fontWeight: 500,
        }}>Cancel</button>
        <button
          onClick={handleSubmit}
          disabled={!selectedProjectId || !content.trim() || submitting}
          style={{
            padding: '8px 24px', borderRadius: 'var(--radius-md)',
            border: 'none',
            background: selectedProjectId && content.trim() ? 'var(--color-brand)' : 'var(--color-surface-2)',
            color: selectedProjectId && content.trim() ? '#fff' : 'var(--color-text-muted)',
            cursor: selectedProjectId && content.trim() ? 'pointer' : 'default',
            fontSize: 'var(--text-sm)', fontWeight: 600, fontFamily: 'inherit',
            transition: 'all var(--transition-fast)',
          }}
        >
          {submitting ? <ButtonLoader label="Shipping..." /> : 'Publish ship'}
        </button>
      </div>
    </Modal>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: 'var(--text-sm)', fontWeight: 600,
  color: 'var(--color-text-primary)',
  marginBottom: 'var(--space-2)',
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 12px',
  borderRadius: 'var(--radius-md)',
  border: '1px solid var(--color-border)',
  background: 'var(--color-surface)',
  fontSize: 'var(--text-sm)',
  fontFamily: 'inherit',
  color: 'var(--color-text-primary)',
  outline: 'none',
  transition: 'border-color var(--transition-fast)',
}
