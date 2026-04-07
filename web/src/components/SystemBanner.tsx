/**
 * SystemBanner — surfaces backend system state to the user.
 * Shows when feed is degraded, rate limited, or system is under load.
 */

type BannerVariant = 'warning' | 'error' | 'info'

interface SystemBannerProps {
  variant: BannerVariant
  message: string
  detail?: string
  onDismiss?: () => void
}

const config: Record<BannerVariant, { bg: string; border: string; color: string; icon: string }> = {
  warning: { bg: 'var(--color-warning-bg)', border: '#fde68a', color: '#92400e', icon: '⚠' },
  error:   { bg: 'var(--color-error-bg)',   border: 'var(--color-error-border)', color: '#991b1b', icon: '✕' },
  info:    { bg: 'var(--color-info-bg)',     border: '#bfdbfe', color: '#1e40af', icon: 'ℹ' },
}

export function SystemBanner({ variant, message, detail, onDismiss }: SystemBannerProps) {
  const c = config[variant]
  return (
    <div role="alert" style={{
      display: 'flex', alignItems: 'flex-start', gap: 'var(--space-3)',
      padding: 'var(--space-3) var(--space-4)',
      background: c.bg, border: `1px solid ${c.border}`,
      borderRadius: 'var(--radius-md)', color: c.color,
      fontSize: 'var(--text-sm)', marginBottom: 'var(--space-4)',
      animation: 'fadeIn 200ms ease',
    }}>
      <span style={{ fontSize: 14, flexShrink: 0, marginTop: 1 }}>{c.icon}</span>
      <div style={{ flex: 1 }}>
        <span style={{ fontWeight: 'var(--weight-semibold)' }}>{message}</span>
        {detail && <span style={{ marginLeft: 6, opacity: 0.8 }}>{detail}</span>}
      </div>
      {onDismiss && (
        <button onClick={onDismiss} aria-label="Dismiss" style={{
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'inherit', opacity: 0.6, fontSize: 14, padding: 0,
          lineHeight: 1,
        }}>✕</button>
      )}
    </div>
  )
}
