import { useEffect, useMemo, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Project, ProjectAssignment, ProjectRole, User } from '../api/types'
import { PROJECT_ROLES } from '../api/types'
import Modal from './Modal'

type Selection = Record<string, ProjectRole>

/** Admin-side bulk assignment: tick the projects a user should see and set a role for each. */
export default function AssignProjectsModal({
  user,
  onClose,
  onSaved,
}: {
  user: User
  onClose: () => void
  onSaved: (count: number) => void
}) {
  const [projects, setProjects] = useState<Project[]>([])
  const [selection, setSelection] = useState<Selection>({})
  const [filter, setFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.listProjects(), api.adminUserProjects(user.id)])
      .then(([all, assigned]: [Project[], ProjectAssignment[]]) => {
        if (cancelled) return
        setProjects(all)
        const initial: Selection = {}
        assigned.forEach((a) => (initial[a.projectKey] = a.projectRole))
        setSelection(initial)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load projects'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [user.id])

  const visible = useMemo(() => {
    const needle = filter.trim().toLowerCase()
    if (!needle) return projects
    return projects.filter(
      (p) => p.name.toLowerCase().includes(needle) || p.projectKey.toLowerCase().includes(needle),
    )
  }, [projects, filter])

  function toggle(key: string, checked: boolean) {
    setSelection((prev) => {
      const next = { ...prev }
      if (checked) next[key] = prev[key] ?? 'MEMBER'
      else delete next[key]
      return next
    })
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const assignments = Object.entries(selection).map(([projectKey, projectRole]) => ({
        projectKey,
        projectRole,
      }))
      const saved = await api.adminSetUserProjects(user.id, assignments)
      onSaved(saved.length)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save assignments')
    } finally {
      setBusy(false)
    }
  }

  const selectedCount = Object.keys(selection).length

  return (
    <Modal title={`Projects for ${user.displayName}`} onClose={onClose}>
      {loading ? (
        <p className="muted">Loading projects…</p>
      ) : (
        <form className="form" onSubmit={submit}>
          <p className="muted">
            {user.displayName} sees only the projects ticked here. Unticking one removes their
            access immediately.
          </p>

          <input
            className="search"
            placeholder="Filter projects…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />

          <ul className="assign-list">
            {visible.map((project) => {
              const checked = selection[project.projectKey] !== undefined
              return (
                <li key={project.id} className="assign-row">
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(e) => toggle(project.projectKey, e.target.checked)}
                    />
                    <span className="project-key">{project.projectKey}</span>
                    <span className="assign-name">{project.name}</span>
                  </label>
                  <select
                    value={selection[project.projectKey] ?? 'MEMBER'}
                    disabled={!checked}
                    onChange={(e) =>
                      setSelection((prev) => ({
                        ...prev,
                        [project.projectKey]: e.target.value as ProjectRole,
                      }))
                    }
                  >
                    {PROJECT_ROLES.map((r) => (
                      <option key={r}>{r}</option>
                    ))}
                  </select>
                </li>
              )
            })}
            {visible.length === 0 && <li className="muted">No projects match.</li>}
          </ul>

          <p className="muted">
            Choosing LEAD makes them a project lead, alongside any existing leads. A project must
            keep at least one lead.
          </p>

          {error && <p className="error">{error}</p>}
          <div className="form-actions">
            <span className="muted assign-count">{selectedCount} assigned</span>
            <button type="button" className="btn btn-ghost" onClick={onClose}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={busy}>
              {busy ? 'Saving…' : 'Save assignments'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}
