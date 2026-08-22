import { useState } from 'react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import BrandMark, { BrandName } from '../components/BrandMark'

export default function LoginPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [form, setForm] = useState({
    usernameOrEmail: '',
    password: '',
    username: '',
    email: '',
    displayName: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (mode === 'login') {
        await login(form.usernameOrEmail, form.password)
      } else {
        await register({
          username: form.username,
          email: form.email,
          password: form.password,
          displayName: form.displayName || form.username,
        })
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-screen">
      <form className="card auth-card" onSubmit={submit}>
        <div className="auth-brand">
          <BrandMark size={32} />
          <h1>
            <BrandName />
          </h1>
        </div>

        <div className="tabs">
          <button
            type="button"
            className={mode === 'login' ? 'tab tab-active' : 'tab'}
            onClick={() => setMode('login')}
          >
            Sign in
          </button>
          <button
            type="button"
            className={mode === 'register' ? 'tab tab-active' : 'tab'}
            onClick={() => setMode('register')}
          >
            Create account
          </button>
        </div>

        {mode === 'login' ? (
          <>
            <label>
              Username or email
              <input value={form.usernameOrEmail} onChange={set('usernameOrEmail')} autoFocus required />
            </label>
            <label>
              Password
              <input type="password" value={form.password} onChange={set('password')} required />
            </label>
          </>
        ) : (
          <>
            <label>
              Username
              <input value={form.username} onChange={set('username')} minLength={3} required />
            </label>
            <label>
              Display name
              <input value={form.displayName} onChange={set('displayName')} required />
            </label>
            <label>
              Email
              <input type="email" value={form.email} onChange={set('email')} required />
            </label>
            <label>
              Password
              <input
                type="password"
                value={form.password}
                onChange={set('password')}
                minLength={8}
                required
              />
              <small className="muted">At least 8 characters.</small>
            </label>
          </>
        )}

        {error && <p className="error">{error}</p>}

        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>

        <p className="muted auth-hint">
          Demo logins: <code>admin / admin123</code>, <code>alice / password</code>
        </p>
      </form>
    </div>
  )
}
