import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Project, Ticket, TicketStatus, User } from '../api/types'
import { STATUS_LABELS, TICKET_STATUSES } from '../api/types'
import Avatar from '../components/Avatar'
import CreateTicketModal from '../components/CreateTicketModal'
import { PriorityBadge, TypeBadge } from '../components/Badges'

export default function ProjectBoardPage() {
  const { projectKey = '' } = useParams()
  const [project, setProject] = useState<Project | null>(null)
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [members, setMembers] = useState<User[]>([])
  const [view, setView] = useState<'board' | 'list'>('board')
  const [q, setQ] = useState('')
  const [assigneeId, setAssigneeId] = useState<number | ''>('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)

  const loadTickets = useCallback(async () => {
    try {
      const page = await api.listTickets(projectKey, { q, assigneeId, size: 200 })
      setTickets(page.content)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load tickets')
    }
  }, [projectKey, q, assigneeId])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([api.getProject(projectKey), api.listMembers(projectKey)])
      .then(([p, m]) => {
        if (cancelled) return
        setProject(p)
        setMembers(m.map((entry) => entry.user))
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load project'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [projectKey])

  // Debounced so typing in the search box does not fire a request per keystroke.
  useEffect(() => {
    const handle = setTimeout(() => void loadTickets(), 200)
    return () => clearTimeout(handle)
  }, [loadTickets])

  const byStatus = useMemo(() => {
    const groups: Record<TicketStatus, Ticket[]> = {
      BACKLOG: [], TODO: [], IN_PROGRESS: [], IN_REVIEW: [], DONE: [],
    }
    for (const ticket of tickets) groups[ticket.status].push(ticket)
    return groups
  }, [tickets])

  async function moveTicket(ticketKey: string, status: TicketStatus) {
    const previous = tickets
    // Optimistic: the card follows the cursor immediately, roll back if the server says no.
    setTickets((current) => current.map((t) => (t.ticketKey === ticketKey ? { ...t, status } : t)))
    try {
      await api.setTicketStatus(ticketKey, status)
    } catch (err) {
      setTickets(previous)
      setError(err instanceof ApiError ? err.message : 'Could not move ticket')
    }
  }

  if (loading) return <p className="muted">Loading…</p>
  if (!project) return <p className="error">{error ?? 'Project not found'}</p>

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <div className="breadcrumb">
            <Link to="/projects">Projects</Link> <span>/</span> <span>{project.projectKey}</span>
          </div>
          <h1>{project.name}</h1>
          <p className="muted">{project.description || 'No description'}</p>
        </div>
        <div className="header-actions">
          <Link className="btn btn-ghost" to={`/projects/${project.projectKey}/settings`}>
            Settings
          </Link>
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            Create ticket
          </button>
        </div>
      </div>

      <div className="toolbar">
        <input
          className="search"
          placeholder="Search by title or key…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">All assignees</option>
          {members.map((m) => (
            <option key={m.id} value={m.id}>
              {m.displayName}
            </option>
          ))}
        </select>
        <div className="segmented">
          <button className={view === 'board' ? 'seg seg-active' : 'seg'} onClick={() => setView('board')}>
            Board
          </button>
          <button className={view === 'list' ? 'seg seg-active' : 'seg'} onClick={() => setView('list')}>
            List
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      {view === 'board' ? (
        <div className="board">
          {TICKET_STATUSES.map((status) => (
            <BoardColumn
              key={status}
              status={status}
              tickets={byStatus[status]}
              onDropTicket={(ticketKey) => moveTicket(ticketKey, status)}
            />
          ))}
        </div>
      ) : (
        <TicketTable tickets={tickets} />
      )}

      {creating && (
        <CreateTicketModal
          projectKey={project.projectKey}
          members={members}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            void loadTickets()
          }}
        />
      )}
    </div>
  )
}

function BoardColumn({
  status,
  tickets,
  onDropTicket,
}: {
  status: TicketStatus
  tickets: Ticket[]
  onDropTicket: (ticketKey: string) => void
}) {
  const [over, setOver] = useState(false)

  return (
    <section
      className={over ? 'column column-over' : 'column'}
      onDragOver={(e) => {
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault()
        setOver(false)
        const ticketKey = e.dataTransfer.getData('text/ticket-key')
        if (ticketKey) onDropTicket(ticketKey)
      }}
    >
      <header className="column-head">
        <span>{STATUS_LABELS[status]}</span>
        <span className="count">{tickets.length}</span>
      </header>
      <div className="column-body">
        {tickets.map((ticket) => (
          <article
            key={ticket.id}
            className="ticket-card"
            draggable
            onDragStart={(e) => e.dataTransfer.setData('text/ticket-key', ticket.ticketKey)}
          >
            <Link to={`/tickets/${ticket.ticketKey}`} className="ticket-title">
              {ticket.title}
            </Link>
            {ticket.epic && (
              <Link to={`/tickets/${ticket.epic.ticketKey}`} className="epic-chip epic-chip-sm">
                {ticket.epic.title}
              </Link>
            )}
            <div className="ticket-meta">
              <TypeBadge type={ticket.type} />
              <span className="ticket-key">{ticket.ticketKey}</span>
              <PriorityBadge priority={ticket.priority} />
              <span className="spacer" />
              <Avatar user={ticket.assignee} size={24} />
            </div>
          </article>
        ))}
        {tickets.length === 0 && <p className="column-empty muted">Drop tickets here</p>}
      </div>
    </section>
  )
}

function TicketTable({ tickets }: { tickets: Ticket[] }) {
  if (tickets.length === 0) return <p className="muted">No tickets match these filters.</p>
  return (
    <div className="card table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Key</th>
            <th>Type</th>
            <th>Title</th>
            <th>Status</th>
            <th>Priority</th>
            <th>Assignee</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr key={ticket.id}>
              <td>
                <Link to={`/tickets/${ticket.ticketKey}`} className="ticket-key">
                  {ticket.ticketKey}
                </Link>
              </td>
              <td><TypeBadge type={ticket.type} /></td>
              <td>
                <Link to={`/tickets/${ticket.ticketKey}`}>{ticket.title}</Link>
              </td>
              <td><span className="status-pill">{STATUS_LABELS[ticket.status]}</span></td>
              <td><PriorityBadge priority={ticket.priority} /> {ticket.priority}</td>
              <td className="cell-user">
                <Avatar user={ticket.assignee} size={22} />
                {ticket.assignee?.displayName ?? 'Unassigned'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
