import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const RANK_COLORS: Record<string, string> = {
  DOCKED:       'var(--rank-docked)',
  LAUNCHER:     'var(--rank-launcher)',
  ORBITER:      'var(--rank-orbiter)',
  NAVIGATOR:    'var(--rank-navigator)',
  COMMANDER:    'var(--rank-commander)',
  FLEET_ADMIRAL:'var(--rank-admiral)',
}

/**
 * LinkedIn-style left profile card.
 * Shows avatar, name, rank, stats, and quick nav links.
 */
export function LeftPanel() {
  const { user } = useAuth()
  if (!user) return null

  const rankColor = RANK_COLORS[user.rank] || 'var(--color-brand)'

  return (
    <aside style={{
      width: 'var(--col-left-width)',
      flexShrink: 0,
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-2)',
    }}>
      {/* Profile card */}
      <div style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
        boxShadow: 'var(--shadow-sm)',
      }}>
        {/* Banner */}
        <div style={{
          height: 56,
          background: `linear-gradient(135deg, ${rankColor}30, var(--color-brand-light))`,
          borderBottom: '1px solid var(--color-border)',
        }} />

        {/* Avatar + name */}
        <div style={{ padding: '0 var(--space-4) var(--space-4)' }}>
          <div style={{ marginTop: -24, marginBottom: 'var(--space-3)' }}>
            <img
              src={user.avatarUrl}
              alt={user.username}
              style={{
                width: 56, height: 56, borderRadius: '50%', objectFit: 'cover',
                border: `3px solid var(--color-surface)`,
                boxShadow: `0 0 0 2px ${rankColor}`,
              }}
            />
          </div>

          <Link
            to={`/profile/${user.username}`}
            style={{ textDecoration: 'none' }}
          >
            <div style={{ fontWeight: 700, fontSize: 'var(--text-base)', color: 'var(--color-text-primary)', lineHeight: 1.3 }}>
              {user.username}
            </div>
          </Link>

          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            marginTop: 4,
            padding: '2px 8px',
            borderRadius: 'var(--radius-full)',
            background: rankColor + '18',
            border: `1px solid ${rankColor}30`,
          }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: rankColor, flexShrink: 0 }} />
            <span style={{ fontSize: 'var(--text-xs)', fontWeight: 600, color: rankColor }}>
              {user.rank} · Lv.{user.level}
            </span>
          </div>

          {/* Stats */}
          <div style={{
            marginTop: 'var(--space-4)',
            paddingTop: 'var(--space-3)',
            borderTop: '1px solid var(--color-border)',
            display: 'flex', flexDirection: 'column', gap: 6,
          }}>
            <StatRow label="ShipScore" value={user.shipScore?.toLocaleString() ?? '0'} />
          </div>
        </div>
      </div>

      {/* Quick links card */}
      <div style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-3) 0',
        boxShadow: 'var(--shadow-sm)',
      }}>
        {[
          { to: '/feed',           label: 'My Feed' },
          { to: '/projects',       label: 'My Projects' },
          { to: '/collaborations', label: 'My Network' },
          { to: '/ships/new',      label: 'Ship Something', highlight: true },
        ].map(item => (
          <Link
            key={item.to}
            to={item.to}
            style={{
              display: 'block',
              padding: '8px var(--space-4)',
              fontSize: 'var(--text-sm)',
              fontWeight: item.highlight ? 600 : 500,
              color: item.highlight ? 'var(--color-brand)' : 'var(--color-text-secondary)',
              textDecoration: 'none',
              transition: 'background var(--transition-fast)',
              borderRadius: 0,
            }}
            onMouseEnter={e => e.currentTarget.style.background = 'var(--color-surface-2)'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            {item.label}
          </Link>
        ))}
      </div>
    </aside>
  )
}

function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>{label}</span>
      <span style={{ fontSize: 'var(--text-xs)', fontWeight: 700, color: 'var(--color-brand-dim)', fontFamily: 'var(--font-mono)' }}>
        {value}
      </span>
    </div>
  )
}
