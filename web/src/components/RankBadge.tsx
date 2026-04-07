const RANK_COLORS: Record<string, string> = {
  DOCKED:       'var(--rank-docked)',
  LAUNCHER:     'var(--rank-launcher)',
  ORBITER:      'var(--rank-orbiter)',
  NAVIGATOR:    'var(--rank-navigator)',
  COMMANDER:    'var(--rank-commander)',
  FLEET_ADMIRAL:'var(--rank-admiral)',
}

interface RankBadgeProps {
  rank: string
  level: number
  size?: 'sm' | 'md'
}

export function RankBadge({ rank, level, size = 'sm' }: RankBadgeProps) {
  const color = RANK_COLORS[rank] ?? 'var(--color-text-muted)'
  const isMd = size === 'md'

  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center',
      background: 'var(--color-surface-4)',
      borderLeft: `2px solid ${color}`,
      borderRadius: 'var(--radius-sm)',
      padding: isMd ? '4px 10px 4px 8px' : '2px 8px 2px 6px',
      fontSize: isMd ? 'var(--text-sm)' : 'var(--text-xs)',
      fontWeight: 600,
      color,
      gap: 5,
      letterSpacing: '0.02em',
      whiteSpace: 'nowrap',
    }}>
      {rank.replace('_', ' ')}
      <span style={{ fontFamily: 'var(--font-mono)', opacity: 0.8 }}>
        L{level}
      </span>
    </span>
  )
}

export { RANK_COLORS }
