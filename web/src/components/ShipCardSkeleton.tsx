/** Loading skeleton for ShipCard */
export function ShipCardSkeleton() {
  const pulse: React.CSSProperties = {
    background: 'linear-gradient(90deg, var(--color-surface-2) 25%, var(--color-surface-3) 50%, var(--color-surface-2) 75%)',
    backgroundSize: '200% 100%',
    animation: 'pulse 1.5s ease infinite',
    borderRadius: 'var(--radius-sm)',
  }
  return (
    <div style={{
      background: 'var(--color-surface)',
      borderRadius: 'var(--radius-lg)',
      padding: 'var(--space-5)',
      marginBottom: 'var(--space-3)',
    }}>
      <div style={{ display: 'flex', gap: 'var(--space-3)', marginBottom: 'var(--space-4)', alignItems: 'center' }}>
        <div style={{ ...pulse, width: 38, height: 38, borderRadius: '50%', flexShrink: 0 }} />
        <div style={{ flex: 1 }}>
          <div style={{ ...pulse, height: 12, width: '28%', marginBottom: 6 }} />
          <div style={{ ...pulse, height: 10, width: '18%' }} />
        </div>
        <div style={{ ...pulse, height: 10, width: 32 }} />
      </div>
      <div style={{ ...pulse, height: 13, width: '100%', marginBottom: 7 }} />
      <div style={{ ...pulse, height: 13, width: '88%', marginBottom: 7 }} />
      <div style={{ ...pulse, height: 13, width: '55%', marginBottom: 16 }} />
      <div style={{ display: 'flex', gap: 'var(--space-4)', paddingTop: 'var(--space-3)', borderTop: '1px solid var(--color-border)' }}>
        <div style={{ ...pulse, height: 22, width: 48 }} />
        <div style={{ ...pulse, height: 22, width: 36 }} />
      </div>
    </div>
  )
}
