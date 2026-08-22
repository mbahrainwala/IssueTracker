import { useCallback, useEffect, useState } from 'react'
import { ApiError, api, type LaneInput, type StarterTicketInput } from '../api/client'
import type { Template } from '../api/types'
import { TICKET_PRIORITIES, TICKET_TYPES } from '../api/types'
import LaneEditor, { toLaneInputs } from '../components/LaneEditor'
import Modal from '../components/Modal'

/**
 * Where an administrator defines the boards everyone else starts from.
 *
 * A template is a starting point, not a binding: a project copies its lanes at creation and
 * goes its own way afterwards. That is worth saying on the page, because "editing a template"
 * sounds like it should reshape existing boards, and deliberately does not.
 */
export default function AdminTemplatesPage() {
  const [templates, setTemplates] = useState<Template[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Template | 'new' | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setTemplates(await api.listTemplates())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load templates')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function remove(template: Template) {
    if (!confirm(`Delete the "${template.name}" template? Existing projects are unaffected.`)) return
    try {
      await api.deleteTemplate(template.id)
      setError(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete the template')
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Project templates</h1>
          <p className="muted">
            The boards new projects can start from. A project <strong>copies</strong> the lanes
            when it is created — editing a template here never rearranges a board somebody is
            already working on.
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          New template
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {loading ? (
        <p className="muted">Loading templates…</p>
      ) : (
        <div className="template-grid">
          {templates.map((template) => (
            <div key={template.id} className="card template-card">
              <div className="template-head">
                <h3>{template.name}</h3>
                {template.builtIn && <span className="badge">built in</span>}
              </div>
              <p className="muted">{template.description || 'No description'}</p>
              <ol className="lane-preview">
                {[...template.lanes]
                  .sort((a, b) => a.order - b.order)
                  .map((lane) => (
                    <li key={lane.id} className="lane-chip">
                      {lane.name}
                      {lane.initial && <span className="lane-tag">start</span>}
                      {lane.done && <span className="lane-tag">finished</span>}
                    </li>
                  ))}
              </ol>
              {template.starterTickets.length > 0 && (
                <p className="muted">
                  Starts every project with {template.starterTickets.length} ticket
                  {template.starterTickets.length === 1 ? '' : 's'}:{' '}
                  {template.starterTickets.map((t) => t.title).join(', ')}
                </p>
              )}
              <div className="form-actions">
                {!template.builtIn && (
                  <button className="btn btn-ghost btn-danger" onClick={() => remove(template)}>
                    Delete
                  </button>
                )}
                <button className="btn btn-ghost" onClick={() => setEditing(template)}>
                  Edit
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <TemplateModal
          template={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void reload()
          }}
        />
      )}
    </div>
  )
}

function TemplateModal({
  template,
  onClose,
  onSaved,
}: {
  template: Template | null
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(template?.name ?? '')
  const [description, setDescription] = useState(template?.description ?? '')
  const [lanes, setLanes] = useState<LaneInput[]>(
    template
      ? toLaneInputs([...template.lanes].sort((a, b) => a.order - b.order))
      : [
          { name: 'To Do', initial: true, done: false },
          { name: 'In Progress', initial: false, done: false },
          { name: 'Done', initial: false, done: true },
        ],
  )
  const [starters, setStarters] = useState<StarterTicketInput[]>(
    template
      ? template.starterTickets.map((t) => ({
          title: t.title,
          description: t.description ?? '',
          type: t.type,
          priority: t.priority,
          lane: t.lane ?? '',
        }))
      : [],
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function updateStarter(index: number, patch: Partial<StarterTicketInput>) {
    setStarters(starters.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // A template's lane ids are its own; when saving they identify lanes to keep, and a
      // lane added in this dialog has none.
      const body = {
        name,
        description: description || undefined,
        lanes,
        // Empty descriptions travel as undefined so the server stores null rather than "".
        starterTickets: starters.map((s) => ({ ...s, description: s.description || undefined })),
      }
      if (template) await api.updateTemplate(template.id, body)
      else await api.createTemplate(body)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the template')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={template ? `Edit ${template.name}` : 'New template'} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} required autoFocus />
        </label>
        <label>
          Description
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={1000}
            placeholder="What kind of work is this board for?"
          />
        </label>
        <div>
          <label>Lanes</label>
          <LaneEditor lanes={lanes} onChange={setLanes} disabled={busy} />
        </div>

        <div>
          <label>Starter tickets</label>
          <p className="muted">
            Created in every project made from this template — the work this kind of project
            always begins with. They are ordinary tickets from then on.
          </p>
          <ol className="starter-list">
            {starters.map((starter, index) => (
              <li key={index} className="starter-row">
                <input
                  className="starter-title"
                  value={starter.title}
                  placeholder="Ticket title"
                  maxLength={200}
                  disabled={busy}
                  onChange={(e) => updateStarter(index, { title: e.target.value })}
                />
                <textarea
                  rows={2}
                  value={starter.description ?? ''}
                  placeholder="Description (optional)"
                  disabled={busy}
                  onChange={(e) => updateStarter(index, { description: e.target.value })}
                />
                <div className="starter-fields">
                  <select
                    value={starter.type}
                    disabled={busy}
                    onChange={(e) => updateStarter(index, { type: e.target.value as StarterTicketInput['type'] })}
                  >
                    {TICKET_TYPES.map((t) => (
                      <option key={t}>{t}</option>
                    ))}
                  </select>
                  <select
                    value={starter.priority}
                    disabled={busy}
                    onChange={(e) =>
                      updateStarter(index, { priority: e.target.value as StarterTicketInput['priority'] })
                    }
                  >
                    {TICKET_PRIORITIES.map((p) => (
                      <option key={p}>{p}</option>
                    ))}
                  </select>
                  {/* Only this template's own lanes; a name it does not have is refused. */}
                  <select
                    value={starter.lane}
                    disabled={busy}
                    onChange={(e) => updateStarter(index, { lane: e.target.value })}
                  >
                    <option value="">Starting lane</option>
                    {lanes
                      .filter((lane) => lane.name.trim())
                      .map((lane) => (
                        <option key={lane.name} value={lane.name}>
                          {lane.name}
                        </option>
                      ))}
                  </select>
                  <span className="spacer" />
                  <button
                    type="button"
                    className="link-button"
                    disabled={busy}
                    onClick={() => setStarters(starters.filter((_, i) => i !== index))}
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ol>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={busy}
            onClick={() =>
              setStarters([
                ...starters,
                { title: '', description: '', type: 'TASK', priority: 'MEDIUM', lane: '' },
              ])
            }
          >
            Add starter ticket
          </button>
        </div>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save template'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
