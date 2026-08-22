import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { LinkType, LinkedTicket, TicketLink } from '../api/types'
import { LINK_TYPES } from '../api/types'
import Avatar from './Avatar'
import { PriorityBadge, TypeBadge } from './Badges'

export default function TicketLinks({ ticketKey }: { ticketKey: string }) {
  const [links, setLinks] = useState<TicketLink[]>([])
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .listLinks(ticketKey)
      .then((result) => !cancelled && setLinks(result))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load links'))
    return () => {
      cancelled = true
    }
  }, [ticketKey])

  // Links read as sentences ("is blocked by API-4"), so group by phrasing.
  const grouped = useMemo(() => {
    const map = new Map<string, TicketLink[]>()
    for (const link of links) {
      const bucket = map.get(link.label) ?? []
      bucket.push(link)
      map.set(link.label, bucket)
    }
    return [...map.entries()]
  }, [links])

  async function remove(linkId: number) {
    try {
      await api.deleteLink(linkId)
      setLinks((prev) => prev.filter((l) => l.id !== linkId))
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove link')
    }
  }

  return (
    <section className="links">
      <div className="links-head">
        <h2>Linked tickets ({links.length})</h2>
        <button className="btn btn-ghost" onClick={() => setAdding((v) => !v)}>
          {adding ? 'Cancel' : 'Link a ticket'}
        </button>
      </div>

      {adding && (
        <AddLinkForm
          ticketKey={ticketKey}
          onAdded={(updated) => {
            setLinks(updated)
            setAdding(false)
            setError(null)
          }}
          onError={setError}
        />
      )}

      {error && <p className="error">{error}</p>}

      {grouped.length === 0 && !adding && (
        <p className="muted">No linked tickets. Use “Link a ticket” to connect a bug to its cause.</p>
      )}

      {grouped.map(([label, group]) => (
        <div key={label} className="link-group">
          <h3 className="link-label">{label}</h3>
          {group.map((link) => (
            <div key={link.id} className="link-row">
              <TypeBadge type={link.ticket.type} />
              <Link to={`/tickets/${link.ticket.ticketKey}`} className="ticket-key">
                {link.ticket.ticketKey}
              </Link>
              <Link to={`/tickets/${link.ticket.ticketKey}`} className="link-title">
                {link.ticket.title}
              </Link>
              <span className="spacer" />
              <PriorityBadge priority={link.ticket.priority} />
              <span className="status-pill">{link.ticket.status}</span>
              <Avatar user={link.ticket.assignee} size={22} />
              <button className="link-button" onClick={() => remove(link.id)} title="Remove link">
                ✕
              </button>
            </div>
          ))}
        </div>
      ))}
    </section>
  )
}

function AddLinkForm({
  ticketKey,
  onAdded,
  onError,
}: {
  ticketKey: string
  onAdded: (links: TicketLink[]) => void
  onError: (message: string) => void
}) {
  const [linkType, setLinkType] = useState<LinkType>('RELATES_TO')
  const [q, setQ] = useState('')
  const [results, setResults] = useState<LinkedTicket[]>([])
  const [picked, setPicked] = useState<LinkedTicket | null>(null)
  const [busy, setBusy] = useState(false)

  // Debounced so each keystroke does not hit the search endpoint.
  useEffect(() => {
    if (picked || q.trim().length < 1) {
      setResults([])
      return
    }
    const handle = setTimeout(() => {
      api
        .searchTickets(q.trim(), ticketKey)
        .then(setResults)
        .catch(() => setResults([]))
    }, 200)
    return () => clearTimeout(handle)
  }, [q, picked, ticketKey])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!picked) return
    setBusy(true)
    try {
      onAdded(await api.addLink(ticketKey, linkType, picked.ticketKey))
      setPicked(null)
      setQ('')
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not add link')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="add-link" onSubmit={submit}>
      <div className="add-link-row">
        <span className="muted">This ticket</span>
        <select value={linkType} onChange={(e) => setLinkType(e.target.value as LinkType)}>
          {LINK_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>

        {picked ? (
          <span className="picked-ticket">
            <TypeBadge type={picked.type} />
            <span className="ticket-key">{picked.ticketKey}</span>
            {picked.title}
            <button type="button" className="link-button" onClick={() => setPicked(null)}>
              change
            </button>
          </span>
        ) : (
          <input
            className="search"
            placeholder="Search ticket by key or title…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            autoFocus
          />
        )}

        <button className="btn btn-primary" disabled={!picked || busy}>
          {busy ? 'Linking…' : 'Link'}
        </button>
      </div>

      {!picked && results.length > 0 && (
        <ul className="typeahead">
          {results.map((ticket) => (
            <li key={ticket.id}>
              <button
                type="button"
                onClick={() => {
                  setPicked(ticket)
                  setResults([])
                }}
              >
                <TypeBadge type={ticket.type} />
                <span className="ticket-key">{ticket.ticketKey}</span>
                <span className="typeahead-title">{ticket.title}</span>
                <span className="status-pill">{ticket.status}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </form>
  )
}
