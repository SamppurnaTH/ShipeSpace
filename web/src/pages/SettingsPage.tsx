import { useState, useEffect } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { updateMyProfile, getMe } from '../api/endpoints'

export function SettingsPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  const [bio, setBio] = useState('')
  const [location, setLocation] = useState('')
  const [website, setWebsite] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    getMe().then(r => {
      setBio(r.data.bio ?? '')
      setLocation(r.data.location ?? '')
      setWebsite(r.data.websiteUrl ?? '')
    })
  }, [])

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    try {
      await updateMyProfile({ bio, location, websiteUrl: website })
      toast('Profile updated', 'success')
    } catch {
      toast('Could not save changes', 'error')
    } finally { setSaving(false) }
  }

  return (
    <div style={{ maxWidth: 560, margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--text-xl)', fontWeight: 700, marginBottom: 'var(--space-6)' }}>Settings</h1>

      <div style={{ background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', marginBottom: 'var(--space-4)' }}>
        <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-muted)', marginBottom: 'var(--space-5)', fontFamily: 'var(--font-mono)' }}>
          PROFILE
        </div>

        {/* Avatar — read only from GitHub */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', marginBottom: 'var(--space-6)' }}>
          {user?.avatarUrl && (
            <img src={user.avatarUrl} alt={user.username}
              style={{ width: 64, height: 64, borderRadius: '50%', objectFit: 'cover' }} />
          )}
          <div>
            <div style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>@{user?.username}</div>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 3 }}>
              Avatar synced from GitHub · Username cannot be changed
            </div>
          </div>
        </div>

        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <label style={labelStyle}>
            Bio
            <textarea value={bio} onChange={e => setBio(e.target.value)} maxLength={500} rows={3}
              placeholder="Tell the world what you're building…"
              style={{ ...inputStyle, resize: 'vertical' }} />
            <span style={counterStyle}>{bio.length}/500</span>
          </label>
          <label style={labelStyle}>
            Location
            <input value={location} onChange={e => setLocation(e.target.value)} maxLength={100}
              placeholder="San Francisco, CA" style={inputStyle} />
          </label>
          <label style={labelStyle}>
            Website
            <input value={website} onChange={e => setWebsite(e.target.value)}
              type="url" placeholder="https://yoursite.com" style={inputStyle} />
          </label>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
            <button type="submit" disabled={saving} style={{
              padding: '9px 24px', borderRadius: 'var(--radius-md)', border: 'none',
              background: 'linear-gradient(135deg, var(--color-brand), var(--color-brand-dim))',
              color: '#fff', cursor: saving ? 'not-allowed' : 'pointer',
              fontSize: 'var(--text-sm)', fontWeight: 600, fontFamily: 'inherit',
              opacity: saving ? 0.7 : 1,
            }}>
              {saving ? 'Saving…' : 'Save Changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: 6,
  fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--color-text-secondary)',
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
