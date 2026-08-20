import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { Member, Project, ProjectRole, User } from '../api/types'
import { PROJECT_ROLES } from '../api/types'
import Avatar from '../components/Avatar'

export default function ProjectSettingsPage() {
  const { projectKey = '' } = useParams()
  const navigate = useNavigate()

  const [project, setProject] = useState<Project | null>(null)
  const [members, setMembers] = useState<Member[]>([])
  const [allUsers, setAllUsers] = useState<User[]>([])
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const [newMemberId, setNewMemberId] = useState<number | ''>('')
  const [newMemberRole, setNewMemberRole] = useState<ProjectRole>('MEMBER')

  const reload = useCallback(async () => {
    try {
      const [p, m, users] = await Promise.all([
        api.getProject(projectKey),
        api.listMembers(projectKey),
        api.listUsers(),
      ])
      setProject(p)
      setName(p.name)
      setDescription(p.description ?? '')
      setMembers(m)
      setAllUsers(users)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load project')
    } finally {
      setLoading(false)
    }
  }, [projectKey])

  useEffect(() => {
    void reload()
  }, [reload])

  async function saveDetails(e: React.FormEvent) {
    e.preventDefault()
    try {
      await api.updateProject(projectKey, { name, description: description || undefined })
      setStatus('Project saved.')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Save failed')
    }
  }

  async function addMember(e: React.FormEvent) {
    e.preventDefault()
    if (newMemberId === '') return
    try {
      await api.addMember(projectKey, { userId: newMemberId, projectRole: newMemberRole })
      setNewMemberId('')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add member')
    }
  }

  async function changeRole(userId: number, projectRole: ProjectRole) {
    setStatus(null)
    try {
      await api.addMember(projectKey, { userId, projectRole })
      setError(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change role')
      await reload()
    }
  }

  async function removeMember(userId: number) {
    setStatus(null)
    try {
      await api.removeMember(projectKey, userId)
      setError(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove member')
    }
  }

  async function deleteProject() {
    if (!confirm(`Delete project ${projectKey} and all of its tickets?`)) return
    try {
      await api.deleteProject(projectKey)
      navigate('/projects')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete project')
    }
  }

  if (loading) return <p className="muted">Loading…</p>
  if (!project) return <p className="error">{error ?? 'Project not found'}</p>

  const candidates = allUsers.filter((u) => !members.some((m) => m.user.id === u.id))

  return (
    <div className="page">
      <div className="breadcrumb">
        <Link to="/projects">Projects</Link> <span>/</span>
        <Link to={`/projects/${project.projectKey}`}>{project.projectKey}</Link> <span>/</span>
        <span>Settings</span>
      </div>
      <h1>{project.name} settings</h1>

      {error && <p className="error">{error}</p>}
      {status && <p className="notice">{status}</p>}

      <div className="settings-grid">
        <form className="card form" onSubmit={saveDetails}>
          <h2>Details</h2>
          <label>
            Key
            <input value={project.projectKey} disabled />
            <small className="muted">The key is fixed once tickets exist.</small>
          </label>
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label>
            Description
            <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
          <div className="form-actions">
            <button className="btn btn-primary">Save changes</button>
          </div>
        </form>

        <div className="card">
          <h2>Members</h2>
          <p className="muted">
            Any lead can add people and change roles. A project can have several leads — promote a
            second one before removing the last.
          </p>
          <ul className="member-list">
            {members.map(({ user, projectRole }) => (
              <li key={user.id}>
                <Avatar user={user} />
                <div className="member-info">
                  <strong>{user.displayName}</strong>
                  <span className="muted">@{user.username}</span>
                </div>
                <select
                  value={projectRole}
                  onChange={(e) => changeRole(user.id, e.target.value as ProjectRole)}
                  aria-label={`Role for ${user.displayName}`}
                >
                  {PROJECT_ROLES.map((r) => (
                    <option key={r}>{r}</option>
                  ))}
                </select>
                <button className="link-button" onClick={() => removeMember(user.id)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>

          <form className="form-row add-member" onSubmit={addMember}>
            <select value={newMemberId} onChange={(e) => setNewMemberId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">Add a user…</option>
              {candidates.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
            </select>
            <select value={newMemberRole} onChange={(e) => setNewMemberRole(e.target.value as ProjectRole)}>
              {PROJECT_ROLES.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
            <button className="btn btn-primary" disabled={newMemberId === ''}>
              Add
            </button>
          </form>
        </div>
      </div>

      <div className="card danger-zone">
        <h2>Danger zone</h2>
        <p className="muted">Deleting a project permanently removes its tickets and comments.</p>
        <button className="btn btn-danger" onClick={deleteProject}>
          Delete project
        </button>
      </div>
    </div>
  )
}
