import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import { useAuth } from './auth/AuthContext'
import AdminBrandingPage from './pages/AdminBrandingPage'
import AdminTemplatesPage from './pages/AdminTemplatesPage'
import AdminUsersPage from './pages/AdminUsersPage'
import LoginPage from './pages/LoginPage'
import ProjectsPage from './pages/ProjectsPage'
import ProjectBoardPage from './pages/ProjectBoardPage'
import ProjectSettingsPage from './pages/ProjectSettingsPage'
import TicketPage from './pages/TicketPage'

export default function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return <div className="centered muted">Loading…</div>
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/projects" replace />} />
        <Route path="/login" element={<Navigate to="/projects" replace />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/projects/:projectKey" element={<ProjectBoardPage />} />
        <Route path="/projects/:projectKey/settings" element={<ProjectSettingsPage />} />
        <Route path="/tickets/:ticketKey" element={<TicketPage />} />
        <Route
          path="/admin/users"
          element={
            user.role === 'ADMIN' ? (
              <AdminUsersPage />
            ) : (
              <div className="card">You need administrator access to view this page.</div>
            )
          }
        />
        <Route
          path="/admin/branding"
          element={
            user.role === 'ADMIN' ? (
              <AdminBrandingPage />
            ) : (
              <div className="card">You need administrator access to view this page.</div>
            )
          }
        />
        <Route
          path="/admin/templates"
          element={
            user.role === 'ADMIN' ? (
              <AdminTemplatesPage />
            ) : (
              <div className="card">You need administrator access to view this page.</div>
            )
          }
        />
        <Route path="*" element={<div className="card">Page not found.</div>} />
      </Routes>
    </Layout>
  )
}
