import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useProfile } from '../hooks/useProfile'
import { ShipCard } from '../components/ShipCard'
import { ShipCardSkeleton } from '../components/ShipCardSkeleton'
import { Spinner } from '../components/Spinner'
import { getUserShips, likeShip, unlikeShip } from '../api/endpoints'

const RANK_COLORS: Record<string, string> = {
  DOCKED: '#94a3b8',
  LAUNCHER: '#22c55e',
  ORBITER: '#6366f1',
  NAVIGATOR: '#a78bfa',
  COMMANDER: '#f59e0b',
  FLEET_ADMIRAL: '#ef4444',
}

export function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: me } = useAuth()
  const { profile, rankSummary, loading, toggleFollow } = useProfile(id)
  const [ships, setShips] = useState<any[]>([])
  const [shipsLoading, setShipsLoading] = useState(true)
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())

  useEffect(() => {
    if (!id) return
    setShipsLoading(true)
    getUserShips(id)
      .then(r => setShips(r.data.content ?? []))
      .catch(() => {})
      .finally(() => setShipsLoading(false))
  }, [id])

  const toggleLike = async (shipId: string) => {
    if (!me) return
    const liked = likedIds.has(shipId)
    setLikedIds(p => { const n = new Set(p); liked ? n.delete(shipId) : n.add(shipId); return n })
    setShips(p => p.map(s => s.id === shipId ? { ...s, likeCount: s.likeCount + (liked ? -1 : 1) } : s))
    try { await (liked ? unlikeShip(shipId) : likeShip(shipId)) }
    catch {
      setLikedIds(p => { const n = new Set(p); liked ? n.add(shipId) : n.delete(shipId); return n })
      setShips(p => p.map(s => s.id === shipId ? { ...s, likeCount: s.likeCount + (liked ? 1 : -1) } : s))
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 'var(--space-10)' }}>
        <Spinner size={28} />
      </div>
    )
  }

  if (!profile) {
    return (
      <div style={{ textAlign: 'center', padding: 'var(--space-10)', color: 'var(--color-text-muted)' }}>
        <div style={{ fontSize: 48, marginBottom: 'var(--space-3)' }}>🔍</div>
        <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>User not found</div>
      </div>
    )
  }

  const isOwnProfile = me && profile && me.username === profile.username
  const rankColor = RANK_COLORS[profile.rank] || 'var(--color-brand)'
  const progressPercent = rankSummary
    ? rankSummary.level >= 30 ? 100 : ((rankSummary.shipScore % 40) / 40) * 100
    : 0

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>

      {/* Profile Header */}
      <div style={{
        background: 'var(--color-surface)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border)',
        padding: 'var(--space-8)',
        marginBottom: 'var(--space-6)',
      }}>
        {/* Top row: avatar + info */}
        <div style={{ display: 'flex', gap: 'var(--space-6)', alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <img
            src={profile.avatarUrl}
            alt={profile.username}
            style={{
              width: 80, height: 80, borderRadius: '50%', objectFit: 'cover',
              border: `3px solid ${rankColor}`,
              flexShrink: 0,
            }}
          />
          <div style={{ flex: 1, minWidth: 200 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginBottom: 'var(--space-2)', flexWrap: 'wrap' }}>
              <h1 style={{ fontSize: 'var(--text-2xl)', fontWeight: 700, margin: 0 }}>
                @{profile.username}
              </h1>
              <span style={{
                display: 'inline-flex', alignItems: 'center', gap: 4,
                padding: '3px 10px', borderRadius: 'var(--radius-full)',
                background: rankColor + '18',
                color: rankColor,
                fontSize: 'var(--text-xs)', fontWeight: 700,
                border: `1px solid ${rankColor}30`,
              }}>
                {profile.rank} · Lv.{profile.level}
              </span>
            </div>

            {profile.bio && (
              <p style={{ fontSize: 'var(--text-base)', color: 'var(--color-text-secondary)', margin: '0 0 var(--space-3) 0', lineHeight: 1.5 }}>
                {profile.bio}
              </p>
            )}

            <div style={{ display: 'flex', gap: 'var(--space-4)', fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', flexWrap: 'wrap' }}>
              {profile.location && <span>📍 {profile.location}</span>}
              {profile.websiteUrl && (
                <a href={profile.websiteUrl} target="_blank" rel="noopener noreferrer"
                  style={{ color: 'var(--color-brand)', textDecoration: 'none' }}>
                  🔗 {profile.websiteUrl.replace(/https?:\/\//, '')}
                </a>
              )}
            </div>
          </div>
        </div>

        {/* Stats */}
        <div style={{
          display: 'flex', gap: 'var(--space-6)',
          margin: 'var(--space-6) 0',
          padding: 'var(--space-4) 0',
          borderTop: '1px solid var(--color-border)',
          borderBottom: '1px solid var(--color-border)',
        }}>
          {[
            { label: 'Followers', value: profile.followerCount },
            { label: 'Following', value: profile.followingCount },
            { label: 'Projects',  value: profile.projectCount },
          ].map(stat => (
            <div key={stat.label} style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ fontSize: 'var(--text-xl)', fontWeight: 700, color: 'var(--color-text-primary)' }}>
                {stat.value ?? 0}
              </div>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 2 }}>
                {stat.label}
              </div>
            </div>
          ))}
        </div>

        {/* Follow / Edit button */}
        {isOwnProfile ? (
          <Link to="/settings" style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '8px 20px', borderRadius: 'var(--radius-md)',
            border: '1px solid var(--color-border)',
            background: 'var(--color-surface)',
            color: 'var(--color-text-primary)',
            fontSize: 'var(--text-sm)', fontWeight: 600,
            textDecoration: 'none',
            transition: 'all var(--transition-fast)',
          }}>
            ✏️ Edit Profile
          </Link>
        ) : (
          <button
            onClick={toggleFollow}
            style={{
              padding: '8px 24px', borderRadius: 'var(--radius-md)',
              border: profile.isFollowing ? '1px solid var(--color-border)' : 'none',
              background: profile.isFollowing ? 'var(--color-surface)' : 'var(--color-brand)',
              color: profile.isFollowing ? 'var(--color-text-primary)' : '#fff',
              fontSize: 'var(--text-sm)', fontWeight: 600,
              cursor: 'pointer', fontFamily: 'inherit',
              transition: 'all var(--transition-fast)',
            }}
          >
            {profile.isFollowing ? 'Following ✓' : 'Follow'}
          </button>
        )}
      </div>

      {/* Rank Progression */}
      {rankSummary && (
        <div style={{
          background: 'var(--color-surface)',
          borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--color-border)',
          padding: 'var(--space-5)',
          marginBottom: 'var(--space-6)',
        }}>
          <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, marginBottom: 'var(--space-3)', color: 'var(--color-text-secondary)' }}>
            RANK PROGRESSION
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-2)' }}>
            <span style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: rankColor }}>
              Level {rankSummary.level} · {rankSummary.rank}
            </span>
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              {rankSummary.shipScore} XP
            </span>
          </div>
          <div style={{
            height: 8, borderRadius: 'var(--radius-full)',
            background: 'var(--color-surface-2)',
            overflow: 'hidden',
          }}>
            <div style={{
              height: '100%', borderRadius: 'var(--radius-full)',
              background: `linear-gradient(90deg, ${rankColor}, ${rankColor}cc)`,
              width: `${progressPercent}%`,
              transition: 'width 500ms ease',
            }} />
          </div>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)', marginTop: 'var(--space-2)' }}>
            {rankSummary.level >= 30
              ? '🌟 Maximum Level Reached'
              : `${rankSummary.pointsToNext} XP to Level ${rankSummary.level + 1}`}
          </div>
        </div>
      )}

      {/* Ships */}
      <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--color-text-muted)', marginBottom: 'var(--space-3)', fontFamily: 'var(--font-mono)' }}>
        SHIPS
      </div>

      {shipsLoading && Array.from({ length: 3 }).map((_, i) => <ShipCardSkeleton key={i} />)}

      {!shipsLoading && ships.length === 0 && (
        <div style={{
          background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--color-border)', padding: 'var(--space-10)',
          textAlign: 'center', color: 'var(--color-text-muted)',
        }}>
          <div style={{ fontSize: 32, marginBottom: 'var(--space-3)' }}>🚢</div>
          <div style={{ fontSize: 'var(--text-sm)' }}>
            {isOwnProfile ? "You haven't shipped anything yet." : 'No ships yet.'}
          </div>
          {isOwnProfile && (
            <Link to="/ships/new" style={{
              display: 'inline-block', marginTop: 'var(--space-4)',
              padding: '8px 20px', borderRadius: 'var(--radius-md)',
              background: 'var(--color-brand)', color: '#fff',
              fontSize: 'var(--text-sm)', fontWeight: 600, textDecoration: 'none',
            }}>
              Ship something →
            </Link>
          )}
        </div>
      )}

      {ships.map(s => (
        <ShipCard
          key={s.id}
          ship={s}
          user={profile ? { id: profile.id, username: profile.username, avatarUrl: profile.avatarUrl, rank: profile.rank, level: profile.level } : undefined}
          project={s.project}
          liked={likedIds.has(s.id)}
          canLike={!!me}
          onLike={() => toggleLike(s.id)}
          isOwner={!!me && me.id === profile?.id}
        />
      ))}
    </div>
  )
}