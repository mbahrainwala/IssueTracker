import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Lane, Ticket } from '../api/types'
import Avatar from './Avatar'
import { PriorityBadge, TypeBadge } from './Badges'
import CreateTicketModal from './CreateTicketModal'

/** Shown on an EPIC ticket: every ticket gathered under it, and ways to add more. */
export default function EpicChildren({
  epicKey,
  projectKey,
  lanes,
  archived,
  refreshToken,
}: {
  epicKey: string
  projectKey: string
  /** The project's board, handed to the create-ticket dialog. */
  lanes: Lane[]
  /** An archived epic is frozen; its list becomes read-only. */
  archived: boolean
  refreshToken: number
}) {
  const [children, setChildren] = useState<Ticket[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api
      .listEpicChildren(epicKey)
      .then((result) => !cancelled && setChildren(result))
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : 'Failed to load'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [epicKey, refreshToken])

  async function detach(childKey: string) {
    try {
      setChildren(await api.removeEpicChild(epicKey, childKey))
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove from epic')
    }
  }

  const done = children.filter((c) => c.status === 'DONE').length
  const points = children.reduce((sum, c) => sum + (c.storyPoints ?? 0), 0)
  // What still stands between this epic and being archivable.
  const live = children.filter((c) => !c.archived).length

  return (
    <section className="links">
      <div className="links-head">
        <h2>Tickets in this epic ({children.length})</h2>
        <div className="links-actions">
          {children.length > 0 && (
            <span className="muted">
              {done}/{children.length} done{points > 0 ? ` · ${points} pts` : ''}
            </span>
          )}
          {!archived && (
            <>
              <button className="btn btn-ghost" onClick={() => setAdding((v) => !v)}>
                {adding ? 'Cancel' : 'Add existing'}
              </button>
              <button className="btn btn-ghost" onClick={() => setCreating(true)}>
                New ticket
              </button>
            </>
          )}
        </div>
      </div>

      {!archived && children.length > 0 && live > 0 && (
        <p className="muted">
          {live} of {children.length} not archived — this epic can be archived once they all are.
        </p>
      )}

      {adding && (
        <AddChildrenPanel
          epicKey={epicKey}
          onAdded={(updated) => {
            setChildren(updated)
            setAdding(false)
            setError(null)
          }}
          onError={setError}
        />
      )}

      {error && <p className="error">{error}</p>}
      {loading && <p className="muted">Loading…</p>}

      {!loading && children.length === 0 && (
        <p className="muted">
          Nothing here yet. Use “Add existing” to pull in tickets from this project, or “New
          ticket” to create one directly in this epic.
        </p>
      )}

      {children.length > 0 && (
        <>
          <div className="epic-progress" role="img" aria-label={`${done} of ${children.length} done`}>
            <div style={{ width: `${(done / children.length) * 100}%` }} />
          </div>
          <div className="link-group">
            {children.map((child) => (
              <div key={child.id} className={child.archived ? 'link-row row-disabled' : 'link-row'}>
                <TypeBadge type={child.type} />
                <Link to={`/tickets/${child.ticketKey}`} className="ticket-key">
                  {child.ticketKey}
                </Link>
                <Link to={`/tickets/${child.ticketKey}`} className="link-title">
                  {child.title}
                </Link>
                <span className="spacer" />
                <PriorityBadge priority={child.priority} />
                <span className="status-pill">{child.status}</span>
                <Avatar user={child.assignee} size={22} />
                {child.archived && <span className="badge">archived</span>}
                {!archived && !child.archived && (
                  <button
                    className="link-button"
                    onClick={() => detach(child.ticketKey)}
                    title="Remove from epic (keeps the ticket)"
                  >
                    ✕
                  </button>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {creating && (
        <CreateTicketModal
          projectKey={projectKey}
          lanes={lanes}
          fixedEpicKey={epicKey}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            api.listEpicChildren(epicKey).then(setChildren).catch(() => undefined)
          }}
        />
      )}
    </section>
  )
}

/** Multi-select picker. The API only ever offers tickets from the epic's own project. */
function AddChildrenPanel({
  epicKey,
  onAdded,
  onError,
}: {
  epicKey: string
  onAdded: (children: Ticket[]) => void
  onError: (message: string) => void
}) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState<Ticket[]>([])
  const [picked, setPicked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(false)

  const search = useCallback(async (term: string) => {
    setLoading(true)
    try {
      setResults(await api.listEpicCandidates(epicKey, term))
    } catch {
      setResults([])
    } finally {
      setLoading(false)
    }
  }, [epicKey])

  // Debounced; an empty term still lists recent candidates so the panel is never blank.
  useEffect(() => {
    const handle = setTimeout(() => void search(q.trim()), 200)
    return () => clearTimeout(handle)
  }, [q, search])

  function toggle(key: string) {
    setPicked((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]))
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (picked.length === 0) return
    setBusy(true)
    try {
      onAdded(await api.addEpicChildren(epicKey, picked))
      setPicked([])
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not add tickets to this epic')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="add-link" onSubmit={submit}>
      <div className="add-link-row">
        <input
          className="search"
          placeholder="Search tickets in this project…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          autoFocus
        />
        <button className="btn btn-primary" disabled={picked.length === 0 || busy}>
          {busy ? 'Adding…' : `Add ${picked.length || ''}`.trim()}
        </button>
      </div>

      {loading && <p className="muted">Searching…</p>}
      {!loading && results.length === 0 && (
        <p className="muted">
          No tickets available in this project. Epics and tickets already in this epic are not
          listed.
        </p>
      )}

      {results.length > 0 && (
        <ul className="candidate-list">
          {results.map((ticket) => (
            <li key={ticket.id}>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={picked.includes(ticket.ticketKey)}
                  onChange={() => toggle(ticket.ticketKey)}
                />
                <TypeBadge type={ticket.type} />
                <span className="ticket-key">{ticket.ticketKey}</span>
                <span className="typeahead-title">{ticket.title}</span>
              </label>
              {ticket.epic && (
                <span className="badge" title={`Currently in ${ticket.epic.ticketKey}`}>
                  in {ticket.epic.ticketKey}
                </span>
              )}
              <span className="status-pill">{ticket.status}</span>
            </li>
          ))}
        </ul>
      )}
    </form>
  )
}
