import { useEffect, useRef, useState } from 'react'
import { getUnreadCount } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

const POLL_INTERVAL_MS = 30_000

/**
 * Returns the live unread notification count for the authenticated user.
 * Polls every 30s. Returns 0 when logged out.
 * Shared across nav components — each mount creates its own interval,
 * which is acceptable given the low frequency.
 */
export function useUnreadCount() {
  const { user } = useAuth()
  const [count, setCount] = useState(0)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (!user) { setCount(0); return }

    const fetch = () => {
      getUnreadCount().then(res => setCount(res.data.count ?? 0)).catch(() => {})
    }
    fetch()
    intervalRef.current = setInterval(fetch, POLL_INTERVAL_MS)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [user])

  return count
}
