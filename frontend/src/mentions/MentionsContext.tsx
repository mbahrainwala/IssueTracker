import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../api/client'
import type { Mention } from '../api/types'

interface MentionsState {
  mentions: Mention[]
  /** Ticket keys with something outstanding, for highlighting rows without a lookup per row. */
  flagged: Set<string>
  refresh: () => Promise<void>
}

/**
 * How often to look for new mentions while the tab is in front. Three minutes is a compromise:
 * the request is one indexed query returning a handful of rows, so the cost is negligible, but
 * several open tabs multiply it, and nobody needs to be told about a question within seconds.
 */
const POLL_INTERVAL_MS = 3 * 60_000

const MentionsContext = createContext<MentionsState | undefined>(undefined)

/**
 * The caller's outstanding @mentions, fetched once and shared.
 *
 * Boards, lists and the ticket page all need the same answer - "is this ticket waiting on me?"
 * - so it is fetched here rather than per page, and the result is kept as a Set of ticket keys
 * so highlighting a row is a lookup rather than a scan of the whole list.
 *
 * A failure is silent and leaves the set empty: the highlight is a prompt, and an app that
 * refuses to render a board because one side call failed would be worse than a missing badge.
 */
export function MentionsProvider({ children }: { children: ReactNode }) {
  const [mentions, setMentions] = useState<Mention[]>([])

  const refresh = useCallback(async () => {
    try {
      setMentions(await api.listMentions())
    } catch {
      // Deliberately keeps whatever was already known. A transient failure should not make
      // flags vanish from the board - "nothing is waiting for you" is a claim, and a failed
      // request is not evidence for it. A 401 is handled in the client, which ends the
      // session and unmounts this provider entirely.
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  /**
   * A mention arrives because of somebody else's action, so it is the one thing in the app
   * that changes while you sit still - everything else refetches when you navigate.
   *
   * Polling is gated on the tab being visible, for two reasons. It stops a tab left open
   * overnight from making hundreds of pointless requests, and - the part that actually
   * matters - refreshing on `visibilitychange` means the moment you come back to the tab it
   * is already current, which is exactly when you would notice it was stale.
   */
  useEffect(() => {
    const tick = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    const timer = window.setInterval(tick, POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [refresh])

  const flagged = useMemo(() => new Set(mentions.map((m) => m.ticketKey)), [mentions])

  return (
    <MentionsContext.Provider value={{ mentions, flagged, refresh }}>
      {children}
    </MentionsContext.Provider>
  )
}

export function useMentions(): MentionsState {
  const context = useContext(MentionsContext)
  if (!context) throw new Error('useMentions must be used inside MentionsProvider')
  return context
}
