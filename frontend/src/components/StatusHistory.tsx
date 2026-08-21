import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { StatusChange } from '../api/types'
import { STATUS_LABELS } from '../api/types'
import { formatDateTime } from '../format'
import Avatar from './Avatar'

export default function StatusHistory({
  ticketKey,
  refreshToken,
}: {
  ticketKey: string
  refreshToken: number
}) {
  const [changes, setChanges] = useState<StatusChange[]>([])
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .listStatusHistory(ticketKey)
      .then((result) => !cancelled && setChanges(result))
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : 'Failed to load history'))
    return () => {
      cancelled = true
    }
  }, [ticketKey, refreshToken])

  if (error) return <p className="error">{error}</p>

  const last = changes[changes.length - 1]
  // Newest first when expanded; the most recent move is what people look for.
  const ordered = [...changes].reverse()

  return (
    <section className="history">
      <div className="links-head">
        <h2>Status history ({changes.length})</h2>
        {changes.length > 1 && (
          <button className="btn btn-ghost" onClick={() => setExpanded((v) => !v)}>
            {expanded ? 'Show latest only' : `Show all ${changes.length}`}
          </button>
        )}
      </div>

      {changes.length === 0 && (
        <p className="muted">
          Not moved yet — this ticket is still in the bucket it was created in.
        </p>
      )}

      {last && !expanded && <MoveRow change={last} latest />}

      {expanded && (
        <div className="history-list">
          {ordered.map((change, i) => (
            <MoveRow key={change.id} change={change} latest={i === 0} />
          ))}
        </div>
      )}
    </section>
  )
}

function MoveRow({ change, latest }: { change: StatusChange; latest: boolean }) {
  return (
    <div className={latest ? 'history-row history-row-latest' : 'history-row'}>
      <Avatar user={change.movedBy} size={26} />
      <span className="history-text">
        <strong>{STATUS_LABELS[change.fromStatus]}</strong>
        <span className="history-arrow" aria-label="to">
          →
        </span>
        <strong>{STATUS_LABELS[change.toStatus]}</strong>
        <span className="muted"> by {change.movedBy.displayName}</span>
      </span>
      <span className="spacer" />
      <time className="muted" dateTime={change.movedAt} title={new Date(change.movedAt).toString()}>
        {formatDateTime(change.movedAt)}
      </time>
      {latest && <span className="badge">latest</span>}
    </div>
  )
}
