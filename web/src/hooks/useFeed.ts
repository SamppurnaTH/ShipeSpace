import { useEffect, useRef, useState, useCallback } from 'react'
import {
  getPersonalizedFeed, getTrendingFeed, getTrendingByWindow,
  getFollowingFeed, getLatestFeed, likeShip, unlikeShip
} from '../api/endpoints'
import type { ShipImage } from '../utils/shipImages'

export interface Ship {
  id: string
  content: string
  likeCount: number
  commentCount: number
  trendingScore: number
  createdAt: string
  editWindowOpen?: boolean
  editWindowExpires?: string
  images?: ShipImage[]
  externalLinks?: { url: string; type: string }[]
}

export interface FeedItem {
  ship: Ship
  user?: { id: string; username: string; avatarUrl: string; rank?: string; level?: number }
  project?: { id: string; name: string; techTags: string[] }
  liked?: boolean | null
}

export type FeedType = 'latest' | 'following' | 'trending' | 'personalized'
export type TrendingWindow = 'all' | '24h' | '7d' | '30d'
type FeedStatus = 'idle' | 'loading' | 'error' | 'done'
type SystemState = 'normal' | 'degraded' | 'rate-limited' | 'unavailable'

export function useFeed(isLoggedIn: boolean, feedType: FeedType = 'personalized', trendingWindow: TrendingWindow = 'all') {
  const [items, setItems] = useState<FeedItem[]>([])
  const [status, setStatus] = useState<FeedStatus>('idle')
  const [hasMore, setHasMore] = useState(true)
  const [feedMode, setFeedMode] = useState<string>('')
  const [systemState, setSystemState] = useState<SystemState>('normal')
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())

  const pageRef    = useRef(0)
  const hasMoreRef = useRef(true)
  const loadingRef = useRef(false)

  const getFeedFn = useCallback((pageNum: number, size: number) => {
    switch (feedType) {
      case 'latest':
        return getLatestFeed(pageNum, size)
      case 'following':
        return getFollowingFeed(pageNum, size)
      case 'trending':
        return trendingWindow === 'all'
          ? getTrendingFeed(pageNum, size)
          : getTrendingByWindow(trendingWindow, pageNum, size)
      case 'personalized':
      default:
        return isLoggedIn
          ? getPersonalizedFeed(pageNum, size)
          : getTrendingFeed(pageNum, size)
    }
  }, [feedType, trendingWindow, isLoggedIn])

  const fetchPage = useCallback(async (pageNum: number, reset = false) => {
    if (loadingRef.current || (!hasMoreRef.current && !reset)) return
    loadingRef.current = true
    setStatus('loading')

    try {
      const res = await getFeedFn(pageNum, 20)

      const mode = res.headers['x-feed-mode'] || feedType
      setFeedMode(mode)
      if (mode.includes('fallback') || mode.includes('degraded')) {
        setSystemState('degraded')
      }

      const data = res.data
      const newItems: FeedItem[] = data.content ?? []
      setItems(prev => {
        if (reset) return dedupeFeedItems(newItems)
        return dedupeFeedItems([...prev, ...newItems])
      })

      // Seed liked state from server response (field is null for unauthenticated users)
      if (reset) {
        const serverLiked = new Set(
          newItems.filter(i => i.liked === true).map(i => i.ship.id)
        )
        setLikedIds(serverLiked)
      } else {
        setLikedIds(prev => {
          const next = new Set(prev)
          newItems.forEach(i => { if (i.liked === true) next.add(i.ship.id) })
          return next
        })
      }

      const more = pageNum < (data.totalPages ?? 0) - 1
      hasMoreRef.current = more
      setHasMore(more)
      pageRef.current = pageNum
      setStatus('done')
    } catch (err: any) {
      const s = err?.response?.status
      if (s === 429) setSystemState('rate-limited')
      else if (s === 503) setSystemState('unavailable')
      else if (s >= 500) setSystemState('unavailable')
      hasMoreRef.current = false
      setHasMore(false)
      setStatus('error')
    } finally {
      loadingRef.current = false
    }
  }, [getFeedFn, feedType])

  // Reset on feed type / auth change
  useEffect(() => {
    pageRef.current = 0
    hasMoreRef.current = true
    loadingRef.current = false
    setItems([])
    setHasMore(true)
    setStatus('idle')
    setSystemState('normal')
    fetchPage(0, true)
  }, [isLoggedIn, feedType, trendingWindow, fetchPage])

  const loadMore = useCallback(() => {
    if (!loadingRef.current && hasMoreRef.current) {
      fetchPage(pageRef.current + 1)
    }
  }, [fetchPage])

  const refresh = useCallback(() => {
    pageRef.current = 0
    hasMoreRef.current = true
    setHasMore(true)
    fetchPage(0, true)
  }, [fetchPage])

  const toggleLike = async (item: FeedItem) => {
    const id = item.ship.id
    const liked = likedIds.has(id)

    // Optimistic
    setLikedIds(prev => {
      const n = new Set(prev)
      if (liked) {
        n.delete(id)
      } else {
        n.add(id)
      }
      return n
    })
    setItems(prev => prev.map(i =>
      i.ship.id === id
        ? { ...i, ship: { ...i.ship, likeCount: i.ship.likeCount + (liked ? -1 : 1) } }
        : i
    ))

    try {
      await (liked ? unlikeShip(id) : likeShip(id))
    } catch {
      // Revert
      setLikedIds(prev => {
        const n = new Set(prev)
        if (liked) {
          n.add(id)
        } else {
          n.delete(id)
        }
        return n
      })
      setItems(prev => prev.map(i =>
        i.ship.id === id
          ? { ...i, ship: { ...i.ship, likeCount: i.ship.likeCount + (liked ? 1 : -1) } }
          : i
      ))
    }
  }

  return {
    items, setItems, status, hasMore, feedMode, systemState,
    likedIds, loadMore, refresh, toggleLike,
    isLoading: status === 'loading',
    isError: status === 'error',
  }
}

function dedupeFeedItems(items: FeedItem[]) {
  const seen = new Set<string>()
  return items.filter(item => {
    const id = item.ship?.id
    if (!id || seen.has(id)) return false
    seen.add(id)
    return true
  })
}
