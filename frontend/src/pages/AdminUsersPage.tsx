import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Role, User } from '../api/types'
import { ROLES } from '../api/types'
import AssignProjectsModal from '../components/AssignProjectsModal'
import Avatar from '../components/Avatar'
import Modal from '../components/Modal'
import { useAuth } from '../auth/AuthContext'

type Dialog =
  | { kind: 'create' }
  | { kind: 'edit'; user: User }
  | { kind: 'password'; user: User }
  | { kind: 'projects'; user: User }
  | null

export default function AdminUsersPage() {
  const { user: current } = useAuth()
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [search, setSearch] = useState('')
  const [showDisabled, setShowDisabled] = useState(true)

  const reload = useCallback(async () => {
    try {
      setUsers(await api.adminListUsers())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load users')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase()
    return users.filter((u) => {
      if (!showDisabled && !u.enabled) return false
      if (!needle) return true
      return (
        u.displayName.toLowerCase().includes(needle) ||
        u.username.toLowerCase().includes(needle) ||
        u.email.toLowerCase().includes(needle)
      )
    })
  }, [users, search, showDisabled])

  const activeAdmins = users.filter((u) => u.role === 'ADMIN' && u.enabled).length

  async function toggleEnabled(user: User) {
    setNotice(null)
    try {
      const updated = await api.adminSetUserEnabled(user.id, !user.enabled)
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)))
      setNotice(`${updated.displayName} ${updated.enabled ? 'enabled' : 'disabled'}.`)
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change account status')
    }
  }

  if (loading) return <p className="muted">Loading users…</p>

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>User administration</h1>
          <p className="muted">
            {users.length} account{users.length === 1 ? '' : 's'} · {activeAdmins} active admin
            {activeAdmins === 1 ? '' : 's'}. Accounts are disabled rather than deleted so their
            tickets and comments keep their author.
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setDialog({ kind: 'create' })}>
          New user
        </button>
      </div>

      <div className="toolbar">
        <input
          className="search"
          placeholder="Search name, username or email…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <label className="checkbox">
          <input
            type="checkbox"
            checked={showDisabled}
            onChange={(e) => setShowDisabled(e.target.checked)}
          />
          Show disabled
        </label>
      </div>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>User</th>
              <th>Username</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th className="col-actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((user) => {
              const isSelf = user.id === current?.id
              return (
                <tr key={user.id} className={user.enabled ? undefined : 'row-disabled'}>
                  <td className="cell-user">
                    <Avatar user={user} size={26} />
                    {user.displayName}
                    {isSelf && <span className="badge">you</span>}
                  </td>
                  <td>@{user.username}</td>
                  <td>{user.email}</td>
                  <td>
                    <span className={user.role === 'ADMIN' ? 'badge badge-admin' : 'badge'}>{user.role}</span>
                  </td>
                  <td>
                    <span className={user.enabled ? 'status-pill status-on' : 'status-pill status-off'}>
                      {user.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td className="col-actions">
                    <button className="link-button" onClick={() => setDialog({ kind: 'edit', user })}>
                      Edit
                    </button>
                    <button className="link-button" onClick={() => setDialog({ kind: 'projects', user })}>
                      Projects
                    </button>
                    <button className="link-button" onClick={() => setDialog({ kind: 'password', user })}>
                      Reset password
                    </button>
                    <button
                      className="link-button"
                      onClick={() => toggleEnabled(user)}
                      disabled={isSelf}
                      title={isSelf ? 'You cannot disable your own account' : undefined}
                    >
                      {user.enabled ? 'Disable' : 'Enable'}
                    </button>
                  </td>
                </tr>
              )
            })}
            {visible.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  No users match this filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {dialog?.kind === 'create' && (
        <CreateUserModal
          onClose={() => setDialog(null)}
          onSaved={(created) => {
            setDialog(null)
            setNotice(`Created ${created.displayName}.`)
            void reload()
          }}
        />
      )}
      {dialog?.kind === 'edit' && (
        <EditUserModal
          user={dialog.user}
          isSelf={dialog.user.id === current?.id}
          onClose={() => setDialog(null)}
          onSaved={(updated) => {
            setDialog(null)
            setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)))
            setNotice(`Saved ${updated.displayName}.`)
          }}
        />
      )}
      {dialog?.kind === 'projects' && (
        <AssignProjectsModal
          user={dialog.user}
          onClose={() => setDialog(null)}
          onSaved={(count) => {
            const name = dialog.user.displayName
            setDialog(null)
            setNotice(`${name} is now assigned to ${count} project${count === 1 ? '' : 's'}.`)
          }}
        />
      )}
      {dialog?.kind === 'password' && (
        <ResetPasswordModal
          user={dialog.user}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null)
            setNotice('Password reset.')
          }}
        />
      )}
    </div>
  )
}

function CreateUserModal({ onClose, onSaved }: { onClose: () => void; onSaved: (user: User) => void }) {
  const [form, setForm] = useState({
    username: '',
    displayName: '',
    email: '',
    password: '',
    role: 'USER' as Role,
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onSaved(await api.adminCreateUser(form))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create user')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="New user" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="form-row">
          <label>
            Username
            <input
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              minLength={3}
              autoFocus
              required
            />
            <small className="muted">Permanent — it identifies the account at sign-in.</small>
          </label>
          <label>
            Display name
            <input
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
              required
            />
          </label>
        </div>
        <label>
          Email
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
        </label>
        <div className="form-row">
          <label>
            Temporary password
            <input
              type="text"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              minLength={8}
              required
            />
            <small className="muted">At least 8 characters.</small>
          </label>
          <label>
            Role
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
              {ROLES.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
          </label>
        </div>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create user'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function EditUserModal({
  user,
  isSelf,
  onClose,
  onSaved,
}: {
  user: User
  isSelf: boolean
  onClose: () => void
  onSaved: (user: User) => void
}) {
  const [form, setForm] = useState({
    email: user.email,
    displayName: user.displayName,
    role: user.role,
    enabled: user.enabled,
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onSaved(await api.adminUpdateUser(user.id, form))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save user')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Edit @${user.username}`} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label>
          Display name
          <input
            value={form.displayName}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            autoFocus
            required
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
        </label>
        <div className="form-row">
          <label>
            Role
            <select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
              disabled={isSelf}
            >
              {ROLES.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
          </label>
          <label className="checkbox standalone">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              disabled={isSelf}
            />
            Account active
          </label>
        </div>
        {isSelf && (
          <p className="muted">
            You cannot change your own role or disable yourself — that would lock you out.
          </p>
        )}
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ResetPasswordModal({
  user,
  onClose,
  onSaved,
}: {
  user: User
  onClose: () => void
  onSaved: () => void
}) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.adminResetPassword(user.id, password)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset password')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Reset password for @${user.username}`} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label>
          New password
          <input
            type="text"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            autoFocus
            required
          />
          <small className="muted">
            Shown in clear text so you can copy it — pass it to the user over a trusted channel.
          </small>
        </label>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Resetting…' : 'Reset password'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
