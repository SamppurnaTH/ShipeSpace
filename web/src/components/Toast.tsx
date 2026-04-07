import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

type ToastVariant = 'success' | 'error' | 'warning' | 'info'
interface Toast { id: string; message: string; variant: ToastVariant }

const listeners = new Set<(toast: Toast) => void>()

export const toastSuccess = (message: string) => {
  const t: Toast = { id: Math.random().toString(36).slice(2), message, variant: 'success' }
  listeners.forEach(l => l(t))
}

export const toastError = (message: string) => {
  const t: Toast = { id: Math.random().toString(36).slice(2), message, variant: 'error' }
  listeners.forEach(l => l(t))
}

export const toastInfo = (message: string) => {
  const t: Toast = { id: Math.random().toString(36).slice(2), message, variant: 'info' }
  listeners.forEach(l => l(t))
}

export const useToast = () => {
  const toast = (message: string, variant: ToastVariant = 'info') => {
    if (variant === 'success') toastSuccess(message)
    else if (variant === 'error') toastError(message)
    else toastInfo(message)
  }
  return { toast }
}

const ICONS: Record<ToastVariant, string> = {
  success: '✓', error: '✕', warning: '⚠', info: 'ℹ',
}
const COLORS: Record<ToastVariant, string> = {
  success: 'var(--color-brand)',
  error:   'var(--color-error)',
  warning: 'var(--color-warning)',
  info:    'var(--color-brand-dim)',
}

function ToastItem({ toast, onRemove }: { toast: Toast; onRemove: () => void }) {
  const [leaving, setLeaving] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => { setLeaving(true); setTimeout(onRemove, 300) }, 3700)
    return () => clearTimeout(t)
  }, [onRemove])

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      padding: '10px 16px',
      background: 'var(--color-surface)',
      border: `1px solid var(--color-border)`,
      borderLeft: `3px solid ${COLORS[toast.variant]}`,
      borderRadius: 'var(--radius-md)',
      boxShadow: 'var(--shadow-md)',
      fontSize: 'var(--text-sm)',
      color: 'var(--color-text-primary)',
      minWidth: 260, maxWidth: 380,
      animation: leaving ? 'toastOut 300ms ease forwards' : 'toastIn 200ms ease',
      cursor: 'pointer',
      pointerEvents: 'auto',
    }} onClick={() => { setLeaving(true); setTimeout(onRemove, 300) }}>
      <span style={{ color: COLORS[toast.variant], fontWeight: 700, flexShrink: 0 }}>
        {ICONS[toast.variant]}
      </span>
      {toast.message}
    </div>
  )
}

export function ToastContainer() {
  const [toasts, setToasts] = useState<Toast[]>([])

  useEffect(() => {
    const handle = (t: Toast) => setToasts(prev => [...prev, t])
    listeners.add(handle)
    return () => { listeners.delete(handle) }
  }, [])

  const remove = (id: string) => setToasts(prev => prev.filter(t => t.id !== id))

  return createPortal(
    <div style={{
      position: 'fixed', bottom: 24, right: 24,
      display: 'flex', flexDirection: 'column', gap: 10,
      zIndex: 9999, pointerEvents: 'none',
    }}>
      {toasts.map(t => <ToastItem key={t.id} toast={t} onRemove={() => remove(t.id)} />)}
    </div>,
    document.body
  )
}
