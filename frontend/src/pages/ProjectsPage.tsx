import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Project } from '../api/types'
import Avatar from '../components/Avatar'
import Modal from '../components/Modal'

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  async function reload() {
    setLoading(true)
    try {
      setProjects(await api.listProjects())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load projects')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
  }, [])

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Projects</h1>
          <p className="muted">Every project owns its own ticket numbering, e.g. PROJ1-1232.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>
          New project
        </button>
      </div>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Loading projects…</p>
      ) : projects.length === 0 ? (
        <div className="card empty-state">
          <h3>No projects yet</h3>
          <p className="muted">Create your first project to start filing tickets.</p>
        </div>
      ) : (
        <div className="project-grid">
          {projects.map((project) => (
            <Link key={project.id} to={`/projects/${project.projectKey}`} className="card project-card">
              <div className="project-card-head">
                <span className="project-key">{project.projectKey}</span>
                <span className="muted">{project.ticketCount} tickets</span>
              </div>
              <h3>{project.name}</h3>
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
              </div>
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
      await api.createProject({ projectKey, name, description: description || undefined })
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
