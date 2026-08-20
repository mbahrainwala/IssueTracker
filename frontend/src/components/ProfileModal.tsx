import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { ProjectAssignment, User } from '../api/types'
import Avatar from './Avatar'
import Modal from './Modal'

/**
 * Read-only account summary. Display name, email and role are changed by an administrator,
 * so they are shown rather than edited here; the password is the one thing you own outright.
 */
export default function ProfileModal({
  user,
  onClose,
  onChangePassword,
}: {
  user: User
  onClose: () => void
  onChangePassword: () => void
}) {
  const [projects, setProjects] = useState<ProjectAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .myProjects()
      .then((result) => !cancelled && setProjects(result))
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : 'Failed to load'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  const leadOf = projects.filter((p) => p.projectRole === 'LEAD')

  return (
    <Modal title="Your profile" onClose={onClose}>
      <div className="profile">
        <div className="profile-head">
          <Avatar user={user} size={56} />
          <div>
            <h3>{user.displayName}</h3>
            <div className="muted">@{user.username}</div>
          </div>
        </div>

        <dl className="profile-meta">
          <dt>Email</dt>
          <dd>{user.email}</dd>
          <dt>Account role</dt>
          <dd>
            <span className={user.role === 'ADMIN' ? 'badge badge-admin' : 'badge'}>{user.role}</span>
            {user.role === 'ADMIN' && <span className="muted"> — can see and administer everything</span>}
          </dd>
          <dt>Status</dt>
          <dd>
            <span className={user.enabled ? 'status-pill status-on' : 'status-pill status-off'}>
              {user.enabled ? 'Active' : 'Disabled'}
            </span>
          </dd>
          <dt>Projects</dt>
          <dd>
            {projects.length}
            {leadOf.length > 0 && <span className="muted"> · lead on {leadOf.length}</span>}
          </dd>
        </dl>

        <h3 className="profile-section">Project access</h3>
        {error && <p className="error">{error}</p>}
        {loading && <p className="muted">Loading…</p>}
        {!loading && projects.length === 0 && (
          <p className="muted">
            You are not assigned to any projects yet. An administrator or a project lead can add
            you.
          </p>
        )}
        {projects.length > 0 && (
          <ul className="profile-projects">
            {projects.map((assignment) => (
              <li key={assignment.projectId}>
                <Link to={`/projects/${assignment.projectKey}`} className="project-key" onClick={onClose}>
                  {assignment.projectKey}
                </Link>
                <span className="assign-name">{assignment.projectName}</span>
                <span className={assignment.projectRole === 'LEAD' ? 'badge badge-admin' : 'badge'}>
                  {assignment.projectRole}
                </span>
              </li>
            ))}
          </ul>
        )}

        <p className="muted">
          Your display name, email and role are managed by an administrator. Ask one to change
          them.
        </p>

        <div className="form-actions">
          <button className="btn btn-ghost" onClick={onClose}>
            Close
          </button>
          <button className="btn btn-primary" onClick={onChangePassword}>
            Change password
          </button>
        </div>
      </div>
    </Modal>
  )
}
