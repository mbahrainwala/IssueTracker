import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { User } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import Avatar from './Avatar'
import ChangePasswordModal from './ChangePasswordModal'
import ProfileModal from './ProfileModal'

/** Avatar dropdown in the header: profile, password, sign out. */
export default function UserMenu({ user }: { user: User }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [dialog, setDialog] = useState<'profile' | 'password' | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const ref = useRef<HTMLDivElement>(null)

  // Close on outside click or Escape, the way a menu is expected to behave.
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="user-menu" ref={ref}>
      <button
        className="user-menu-trigger"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
      >
        <Avatar user={user} size={30} />
        <span className="topbar-user">{user.displayName}</span>
        <span className="caret" aria-hidden="true">▾</span>
      </button>

      {open && (
        <div className="menu-dropdown" role="menu">
          <div className="menu-identity">
            <Avatar user={user} size={36} />
            <div>
              <strong>{user.displayName}</strong>
              <div className="muted">@{user.username}</div>
              <div className="muted">{user.email}</div>
            </div>
          </div>
          {user.role === 'ADMIN' && <span className="badge badge-admin menu-role">Administrator</span>}
          <hr />
          <button
            role="menuitem"
            onClick={() => {
              setDialog('profile')
              setOpen(false)
            }}
          >
            Your profile
          </button>
          <button
            role="menuitem"
            onClick={() => {
              setDialog('password')
              setOpen(false)
            }}
          >
            Change password
          </button>
          <hr />
          <button
            role="menuitem"
            className="menu-danger"
            onClick={() => {
              logout()
              navigate('/login')
            }}
          >
            Sign out
          </button>
        </div>
      )}

      {notice && <span className="menu-toast notice">{notice}</span>}

      {dialog === 'profile' && (
        <ProfileModal
          user={user}
          onClose={() => setDialog(null)}
          onChangePassword={() => setDialog('password')}
        />
      )}
      {dialog === 'password' && (
        <ChangePasswordModal
          onClose={() => setDialog(null)}
          onDone={() => {
            setDialog(null)
            setNotice('Password changed.')
            setTimeout(() => setNotice(null), 5000)
          }}
        />
      )}
    </div>
  )
}
