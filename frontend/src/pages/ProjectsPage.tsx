import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Project, Template } from '../api/types'
import Avatar from '../components/Avatar'
import AuthImage from '../components/AuthImage'
import Modal from '../components/Modal'
import { formatDateTime } from '../format'

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [showArchived, setShowArchived] = useState(false)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setProjects(await api.listProjects(showArchived))
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load projects')
    } finally {
      setLoading(false)
    }
  }, [showArchived])

  useEffect(() => {
    void reload()
  }, [reload])

  async function restore(projectKey: string) {
    try {
      await api.restoreProject(projectKey)
      setNotice(`${projectKey} restored.`)
      setError(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not restore project')
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Projects</h1>
          <p className="muted">Every project owns its own ticket numbering, e.g. PROJ1-1232.</p>
        </div>
        {!showArchived && (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            New project
          </button>
        )}
      </div>

      <div className="toolbar">
        <div className="segmented">
          <button
            className={!showArchived ? 'seg seg-active' : 'seg'}
            onClick={() => setShowArchived(false)}
          >
            Active
          </button>
          <button
            className={showArchived ? 'seg seg-active' : 'seg'}
            onClick={() => setShowArchived(true)}
          >
            Archived
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}
      {loading ? (
        <p className="muted">Loading projects…</p>
      ) : projects.length === 0 ? (
        <div className="card empty-state">
          <h3>{showArchived ? 'Nothing archived' : 'No projects yet'}</h3>
          <p className="muted">
            {showArchived
              ? 'Archived projects are hidden from the active list but can be restored at any time.'
              : 'Create your first project to start filing tickets.'}
          </p>
        </div>
      ) : (
        <div className="project-grid">
          {projects.map((project) => (
            <Link
              key={project.id}
              to={`/projects/${project.projectKey}`}
              className={project.archived ? 'card project-card project-card-archived' : 'card project-card'}
            >
              <div className="project-card-head">
                <span className="project-key">{project.projectKey}</span>
                <span className="muted">{project.ticketCount} tickets</span>
              </div>
              <h3>{project.name}</h3>
              {project.hasImage && (
                <AuthImage
                  className="project-image"
                  url={api.projectImageUrl(project.projectKey, project.imageVersion)}
                  alt=""
                />
              )}
              <p className="muted project-desc">{project.description || 'No description'}</p>
              <div className="project-card-foot">
                {project.leads.length === 0 ? (
                  <>
                    <Avatar user={null} size={24} />
                    <span className="muted">No lead</span>
                  </>
                ) : (
                  <>
                    <span className="avatar-stack">
                      {project.leads.slice(0, 3).map((lead) => (
                        <Avatar key={lead.id} user={lead} size={24} />
                      ))}
                    </span>
                    <span className="muted">
                      {project.leads.length === 1
                        ? project.leads[0]!.displayName
                        : `${project.leads.length} leads`}
                    </span>
                  </>
                )}
                {project.archived && (
                  <>
                    <span className="spacer" />
                    <button
                      className="link-button"
                      onClick={(e) => {
                        // The card is a link; restoring should not navigate.
                        e.preventDefault()
                        void restore(project.projectKey)
                      }}
                    >
                      Restore
                    </button>
                  </>
                )}
              </div>
              {project.archived && project.archivedAt && (
                <p className="muted archived-note">
                  Archived {formatDateTime(project.archivedAt)}
                  {project.archivedBy ? ` by ${project.archivedBy.displayName}` : ''}
                </p>
              )}
            </Link>
          ))}
        </div>
      )}

      {creating && (
        <CreateProjectModal
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            void reload()
          }}
        />
      )}
    </div>
  )
}

function CreateProjectModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [projectKey, setProjectKey] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // The board comes from a template. Loading them lets the dialog preview the lanes, so the
  // choice is made on what the board looks like rather than on a name alone.
  const [templates, setTemplates] = useState<Template[]>([])
  const [templateId, setTemplateId] = useState<number | ''>('')

  useEffect(() => {
    let cancelled = false
    api
      .listTemplates()
      .then((result) => {
        if (cancelled) return
        setTemplates(result)
        setTemplateId(result.find((t) => t.name === 'Kanban')?.id ?? result[0]?.id ?? '')
      })
      .catch(() => setTemplates([]))
    return () => {
      cancelled = true
    }
  }, [])

  const chosen = templates.find((t) => t.id === templateId)

  // Suggest a key from the name until the user types one explicitly.
  const [keyTouched, setKeyTouched] = useState(false)
  function onNameChange(value: string) {
    setName(value)
    if (!keyTouched) {
      setProjectKey(value.replace(/[^A-Za-z0-9]/g, '').slice(0, 10).toUpperCase())
    }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.createProject({
        projectKey,
        name,
        description: description || undefined,
        templateId: templateId === '' ? null : templateId,
      })
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create project')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="New project" onClose={onClose}>
      <form onSubmit={submit} className="form">
        <label>
          Name
          <input value={name} onChange={(e) => onNameChange(e.target.value)} autoFocus required />
        </label>
        <label>
          Key
          <input
            value={projectKey}
            onChange={(e) => {
              setKeyTouched(true)
              setProjectKey(e.target.value.toUpperCase())
            }}
            pattern="[A-Za-z][A-Za-z0-9]{1,9}"
            title="2-10 alphanumeric characters, starting with a letter"
            required
          />
          <small className="muted">Tickets will be numbered {projectKey || 'KEY'}-1, {projectKey || 'KEY'}-2, …</small>
        </label>
        <label>
          Description
          <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label>
          Board
          <select
            value={templateId}
            onChange={(e) => setTemplateId(e.target.value ? Number(e.target.value) : '')}
          >
            {templates.map((template) => (
              <option key={template.id} value={template.id}>
                {template.name}
              </option>
            ))}
          </select>
        </label>
        {chosen && (
          <div className="template-preview">
            <p className="muted">{chosen.description}</p>
            <ol className="lane-preview">
              {[...chosen.lanes]
                .sort((a, b) => a.order - b.order)
                .map((lane) => (
                  <li key={lane.id} className="lane-chip">
                    {lane.name}
                  </li>
                ))}
            </ol>
            {chosen.starterTickets.length > 0 && (
              <>
                <p className="muted">
                  and {chosen.starterTickets.length} starter ticket
                  {chosen.starterTickets.length === 1 ? '' : 's'}:
                </p>
                <ul className="starter-preview">
                  {chosen.starterTickets.map((ticket) => (
                    <li key={ticket.id}>
                      {ticket.title}
                      <span className="muted"> · {ticket.lane ?? 'starting lane'}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
            <p className="muted">
              All of this is copied into the project — lanes can be changed later in Settings,
              and the tickets are ordinary tickets you can edit or delete.
            </p>
          </div>
        )}
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create project'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
