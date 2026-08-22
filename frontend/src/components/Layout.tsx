import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import BrandMark, { BrandName } from './BrandMark'
import UserMenu from './UserMenu'

export default function Layout({ children }: { children: ReactNode }) {
  const { user } = useAuth()

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-left">
          <Link to="/projects" className="brand">
            <BrandMark />
            <BrandName />
          </Link>
          <nav className="topnav">
            <NavLink to="/projects" className={({ isActive }) => (isActive ? 'navlink navlink-active' : 'navlink')}>
              Projects
            </NavLink>
            {user?.role === 'ADMIN' && (
              <>
                <NavLink
                  to="/admin/users"
                  className={({ isActive }) => (isActive ? 'navlink navlink-active' : 'navlink')}
                >
                  Users
                </NavLink>
                <NavLink
                  to="/admin/branding"
                  className={({ isActive }) => (isActive ? 'navlink navlink-active' : 'navlink')}
                >
                  Branding
                </NavLink>
              </>
            )}
          </nav>
        </div>
        <div className="topbar-right">{user && <UserMenu user={user} />}</div>
      </header>
      <main className="content">{children}</main>
    </div>
  )
}
