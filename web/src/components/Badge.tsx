import type { ReactNode, CSSProperties } from 'react'

type Variant = 'default' | 'success' | 'warning' | 'error' | 'info' | 'brand'

const variantStyles: Record<Variant, CSSProperties> = {
  default: { background: 'var(--color-surface-2)', color: 'var(--color-text-secondary)' },
  success: { background: 'var(--color-success-bg)', color: '#15803d' },
  warning: { background: 'var(--color-warning-bg)', color: '#92400e' },
  error:   { background: 'var(--color-error-bg)',   color: '#b91c1c' },
  info:    { background: 'var(--color-info-bg)',     color: '#1d4ed8' },
  brand:   { background: 'var(--color-brand-light)', color: 'var(--color-brand)' },
}

interface BadgeProps {
  children: ReactNode
  variant?: Variant
  style?: CSSProperties
}

export function Badge({ children, variant = 'default', style }: BadgeProps) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      fontSize: 'var(--text-xs)', fontWeight: 'var(--weight-medium)',
      padding: '2px 8px', borderRadius: 'var(--radius-full)',
      lineHeight: 1.6,
      ...variantStyles[variant],
      ...style,
    }}>
      {children}
    </span>
  )
}
