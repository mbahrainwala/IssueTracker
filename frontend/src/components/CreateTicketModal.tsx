import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { EpicRef, Lane, Ticket, TicketStatus, User } from '../api/types'
import { TICKET_PRIORITIES, TICKET_TYPES } from '../api/types'
import FormattedTextarea from './FormattedTextarea'
import Modal from './Modal'

/**
 * Shared by the board and the epic page. Passing `fixedEpicKey` files the new ticket straight
 * into that epic and hides the picker, since the epic is already the context you are in.
 */
export default function CreateTicketModal({
  projectKey,
  lanes,
  members,
  fixedEpicKey,
  onClose,
  onCreated,
}: {
  projectKey: string
  /** The project's board. Empty until it loads; the picker then defaults to its first lane. */
  lanes: Lane[]
  members?: User[]
  fixedEpicKey?: string
  onClose: () => void
  onCreated: () => void
}) {
  // Where new tickets belong is the board's decision, not this dialog's.
  const startingLane = lanes.find((lane) => lane.initial)?.name ?? lanes[0]?.name ?? ''

  const [form, setForm] = useState({
    title: '',
    description: '',
    type: 'TASK',
    status: '',
    priority: 'MEDIUM',
    assigneeId: '' as number | '',
    epicKey: '',
  })
  const [epics, setEpics] = useState<EpicRef[]>([])
  const [people, setPeople] = useState<User[]>(members ?? [])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    if (!fixedEpicKey) {
      api
        .listEpics(projectKey)
        .then((result) => !cancelled && setEpics(result))
        .catch(() => setEpics([]))
    }
    if (!members) {
      api
        .listMembers(projectKey)
        .then((result) => !cancelled && setPeople(result.map((m) => m.user)))
        .catch(() => setPeople([]))
    }
    return () => {
      cancelled = true
    }
  }, [projectKey, fixedEpicKey, members])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.createTicket(projectKey, {
        title: form.title,
        description: form.description || undefined,
        type: form.type as Ticket['type'],
        status: (form.status || startingLane) as TicketStatus,
        priority: form.priority as Ticket['priority'],
        assigneeId: form.assigneeId === '' ? null : form.assigneeId,
        // An epic never sits inside another epic.
        epicKey: form.type === 'EPIC' ? null : fixedEpicKey ?? form.epicKey ?? null,
      })
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create ticket')
    } finally {
      setBusy(false)
    }
  }

  const title = fixedEpicKey
    ? `Create ticket in ${fixedEpicKey}`
    : `Create ticket in ${projectKey}`

  return (
    <Modal title={title} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label>
          Title
          <input
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            autoFocus
            required
          />
        </label>
        <label>
          Description
          <FormattedTextarea
            rows={4}
            value={form.description}
            onChange={(description) => setForm({ ...form, description })}
            people={people}
          />
        </label>
        <div className="form-row">
          <label>
            Type
            <select
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value })}
              disabled={Boolean(fixedEpicKey) && form.type === 'EPIC'}
            >
              {TICKET_TYPES.filter((t) => !(fixedEpicKey && t === 'EPIC')).map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            Status
            <select
              value={form.status || startingLane}
              onChange={(e) => setForm({ ...form, status: e.target.value })}
            >
              {lanes.map((lane) => (
                <option key={lane.id} value={lane.name}>
                  {lane.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Priority
            <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>
              {TICKET_PRIORITIES.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="form-row">
          <label>
            Assignee
            <select
              value={form.assigneeId}
              onChange={(e) => setForm({ ...form, assigneeId: e.target.value ? Number(e.target.value) : '' })}
            >
              <option value="">Unassigned</option>
              {people.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.displayName}
                </option>
              ))}
            </select>
          </label>
          {fixedEpicKey ? (
            <label>
              Epic
              <input value={fixedEpicKey} disabled />
            </label>
          ) : (
            form.type !== 'EPIC' && (
              <label>
                Epic
                <select value={form.epicKey} onChange={(e) => setForm({ ...form, epicKey: e.target.value })}>
                  <option value="">No epic</option>
                  {epics.map((epic) => (
                    <option key={epic.id} value={epic.ticketKey}>
                      {epic.ticketKey} · {epic.title}
                    </option>
                  ))}
                </select>
              </label>
            )
          )}
        </div>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
