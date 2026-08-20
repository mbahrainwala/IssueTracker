import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Comment, EpicRef, Member, Ticket, TicketPriority, TicketStatus, TicketType } from '../api/types'
import { STATUS_LABELS, TICKET_PRIORITIES, TICKET_STATUSES, TICKET_TYPES } from '../api/types'
import Avatar from '../components/Avatar'
import { PriorityBadge, TypeBadge } from '../components/Badges'
import EpicChildren from '../components/EpicChildren'
import TicketLinks from '../components/TicketLinks'
import { useAuth } from '../auth/AuthContext'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export default function TicketPage() {
  const { ticketKey = '' } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()

  const [ticket, setTicket] = useState<Ticket | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const [epics, setEpics] = useState<EpicRef[]>([])
  /** Bumped after an edit so the epic's child list refetches. */
  const [childrenToken, setChildrenToken] = useState(0)
  const [editingBody, setEditingBody] = useState(false)
  const [draftTitle, setDraftTitle] = useState('')
  const [draftDescription, setDraftDescription] = useState('')
  const [newComment, setNewComment] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api
      .getTicket(ticketKey)
      .then(async (t) => {
        if (cancelled) return
        setTicket(t)
        setDraftTitle(t.title)
        setDraftDescription(t.description ?? '')
        const [c, m, e] = await Promise.all([
          api.listComments(ticketKey),
          api.listMembers(t.projectKey),
          api.listEpics(t.projectKey),
        ])
        if (cancelled) return
        setComments(c)
        setMembers(m)
        setEpics(e)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load ticket'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [ticketKey])

  async function patch(body: Parameters<typeof api.updateTicket>[1]) {
    try {
      setTicket(await api.updateTicket(ticketKey, body))
      setChildrenToken((n) => n + 1)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Update failed')
    }
  }

  async function saveBody() {
    await patch({ title: draftTitle, description: draftDescription })
    setEditingBody(false)
  }

  async function postComment(e: React.FormEvent) {
    e.preventDefault()
    if (!newComment.trim()) return
    try {
      const created = await api.addComment(ticketKey, newComment.trim())
      setComments((prev) => [...prev, created])
      setNewComment('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post comment')
    }
  }

  async function removeComment(id: number) {
    try {
      await api.deleteComment(id)
      setComments((prev) => prev.filter((c) => c.id !== id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete comment')
    }
  }

  async function deleteTicket() {
    if (!ticket) return
    if (!confirm(`Delete ${ticket.ticketKey}? This cannot be undone.`)) return
    try {
      await api.deleteTicket(ticket.ticketKey)
      navigate(`/projects/${ticket.projectKey}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete ticket')
    }
  }

  if (loading) return <p className="muted">Loading…</p>
  if (!ticket) return <p className="error">{error ?? 'Ticket not found'}</p>

  return (
    <div className="page">
      <div className="breadcrumb">
        <Link to="/projects">Projects</Link> <span>/</span>
        <Link to={`/projects/${ticket.projectKey}`}>{ticket.projectKey}</Link> <span>/</span>
        <span>{ticket.ticketKey}</span>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="ticket-layout">
        <div className="card ticket-main">
          <div className="ticket-head">
            <TypeBadge type={ticket.type} />
            <span className="ticket-key">{ticket.ticketKey}</span>
            {ticket.epic && (
              <Link to={`/tickets/${ticket.epic.ticketKey}`} className="epic-chip" title="Parent epic">
                {ticket.epic.ticketKey} · {ticket.epic.title}
              </Link>
            )}
            <span className="spacer" />
            <button className="btn btn-ghost btn-danger" onClick={deleteTicket}>
              Delete
            </button>
          </div>

          {editingBody ? (
            <div className="form">
              <label>
                Title
                <input value={draftTitle} onChange={(e) => setDraftTitle(e.target.value)} />
              </label>
              <label>
                Description
                <textarea rows={8} value={draftDescription} onChange={(e) => setDraftDescription(e.target.value)} />
              </label>
              <div className="form-actions">
                <button
                  className="btn btn-ghost"
                  onClick={() => {
                    setDraftTitle(ticket.title)
                    setDraftDescription(ticket.description ?? '')
                    setEditingBody(false)
                  }}
                >
                  Cancel
                </button>
                <button className="btn btn-primary" onClick={saveBody}>
                  Save
                </button>
              </div>
            </div>
          ) : (
            <>
              <h1 className="ticket-heading">{ticket.title}</h1>
              <p className="ticket-description">{ticket.description || <span className="muted">No description</span>}</p>
              <button className="btn btn-ghost" onClick={() => setEditingBody(true)}>
                Edit
              </button>
            </>
          )}

          {ticket.type === 'EPIC' && (
            <EpicChildren
              epicKey={ticket.ticketKey}
              projectKey={ticket.projectKey}
              refreshToken={childrenToken}
            />
          )}

          <TicketLinks ticketKey={ticket.ticketKey} />

          <section className="comments">
            <h2>Comments ({comments.length})</h2>
            {comments.map((comment) => (
              <article key={comment.id} className="comment">
                <Avatar user={comment.author} />
                <div className="comment-body">
                  <div className="comment-head">
                    <strong>{comment.author.displayName}</strong>
                    <span className="muted">{formatDate(comment.createdAt)}</span>
                    {(user?.id === comment.author.id || user?.role === 'ADMIN') && (
                      <button className="link-button" onClick={() => removeComment(comment.id)}>
                        Delete
                      </button>
                    )}
                  </div>
                  <p>{comment.body}</p>
                </div>
              </article>
            ))}
            <form className="comment-form" onSubmit={postComment}>
              <textarea
                rows={3}
                placeholder="Leave a comment…"
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
              />
              <button className="btn btn-primary" disabled={!newComment.trim()}>
                Comment
              </button>
            </form>
          </section>
        </div>

        <aside className="card ticket-side">
          <label>
            Status
            <select
              value={ticket.status}
              onChange={(e) => patch({ status: e.target.value as TicketStatus })}
            >
              {TICKET_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Type
            <select value={ticket.type} onChange={(e) => patch({ type: e.target.value as TicketType })}>
              {TICKET_TYPES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            Priority
            <select
              value={ticket.priority}
              onChange={(e) => patch({ priority: e.target.value as TicketPriority })}
            >
              {TICKET_PRIORITIES.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </label>
          <label>
            Assignee
            <select
              value={ticket.assignee?.id ?? ''}
              onChange={(e) =>
                e.target.value
                  ? patch({ assigneeId: Number(e.target.value) })
                  : patch({ clearAssignee: true })
              }
            >
              <option value="">Unassigned</option>
              {members.map(({ user: member }) => (
                <option key={member.id} value={member.id}>
                  {member.displayName}
                </option>
              ))}
            </select>
          </label>
          {/* Epics gather tickets; they never sit inside one themselves. */}
          {ticket.type !== 'EPIC' && (
            <label>
              Epic
              <select
                value={ticket.epic?.ticketKey ?? ''}
                onChange={(e) =>
                  e.target.value ? patch({ epicKey: e.target.value }) : patch({ clearEpic: true })
                }
              >
                <option value="">No epic</option>
                {epics.map((epic) => (
                  <option key={epic.id} value={epic.ticketKey}>
                    {epic.ticketKey} · {epic.title}
                  </option>
                ))}
              </select>
              {epics.length === 0 && (
                <small className="muted">
                  No epics in this project yet — create a ticket of type EPIC first.
                </small>
              )}
            </label>
          )}
          <label>
            Story points
            <input
              type="number"
              min={0}
              value={ticket.storyPoints ?? ''}
              onChange={(e) => patch({ storyPoints: e.target.value ? Number(e.target.value) : null })}
            />
          </label>
          <label>
            Due date
            <input
              type="date"
              value={ticket.dueDate ?? ''}
              onChange={(e) => patch({ dueDate: e.target.value || null })}
            />
          </label>

          <dl className="side-meta">
            <dt>Priority</dt>
            <dd>
              <PriorityBadge priority={ticket.priority} /> {ticket.priority}
            </dd>
            <dt>Reporter</dt>
            <dd className="cell-user">
              <Avatar user={ticket.reporter} size={22} /> {ticket.reporter.displayName}
            </dd>
            <dt>Created</dt>
            <dd>{formatDate(ticket.createdAt)}</dd>
            <dt>Updated</dt>
            <dd>{formatDate(ticket.updatedAt)}</dd>
          </dl>
        </aside>
      </div>
    </div>
  )
}
