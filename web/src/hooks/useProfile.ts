import { useState, useEffect, useCallback } from 'react'
import { getUserProfile, followUser, unfollowUser, getRankSummary } from '../api/endpoints'

interface UserProfile {
  id: string
  username: string
  avatarUrl: string
  bio?: string
  location?: string
  websiteUrl?: string
  followerCount: number
  followingCount: number
  projectCount: number
  shipScore: number
  level: number
  rank: string
  isFollowing?: boolean
}

interface RankSummary {
  shipScore: number
  level: number
  rank: string
  pointsToNext: number
  nextLevelScore: number
}

export function useProfile(userId: string | undefined) {
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [rankSummary, setRankSummary] = useState<RankSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchProfile = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    try {
      const [profileRes, rankRes] = await Promise.allSettled([
        getUserProfile(userId),
        getRankSummary(userId),
      ])
      if (profileRes.status === 'fulfilled') {
        setProfile(profileRes.value.data)
      } else {
        setError('Could not load profile')
      }
      if (rankRes.status === 'fulfilled') {
        setRankSummary(rankRes.value.data)
      }
    } catch {
      setError('Could not load profile')
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => { fetchProfile() }, [fetchProfile])

  const toggleFollow = async () => {
    if (!profile) return
    const wasFollowing = profile.isFollowing

    // Optimistic
    setProfile(prev => prev ? {
      ...prev,
      isFollowing: !wasFollowing,
      followerCount: prev.followerCount + (wasFollowing ? -1 : 1),
    } : prev)

    try {
      await (wasFollowing ? unfollowUser(profile.id) : followUser(profile.id))
    } catch {
      // Revert
      setProfile(prev => prev ? {
        ...prev,
        isFollowing: wasFollowing,
        followerCount: prev.followerCount + (wasFollowing ? 1 : -1),
      } : prev)
    }
  }

  return { profile, rankSummary, loading, error, toggleFollow, refetch: fetchProfile }
}
