import { useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import { useBranding } from '../branding/BrandingContext'

const ACCEPT = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].join(',')

/**
 * Where an administrator sets what the title bar says and shows. Every save refreshes the
 * shared branding, so the header above the form updates as soon as it is applied.
 */
export default function AdminBrandingPage() {
  const { branding, refresh } = useBranding()
  const fileInput = useRef<HTMLInputElement>(null)

  const [companyName, setCompanyName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  // Seed the field from whatever is currently set, once it has loaded.
  useEffect(() => {
    setCompanyName(branding.companyName ?? '')
  }, [branding.companyName])

  async function run(action: () => Promise<unknown>, done = 'Saved') {
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await action()
      await refresh()
      setSaved(done === 'Saved')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the branding')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Branding</h1>
          <p className="muted">
            The company name and logo shown in the title bar, on the sign-in screen and in the
            browser tab. These are visible to anyone who can reach the sign-in page.
          </p>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {saved && <p className="notice">Branding updated.</p>}

      <div className="settings-grid">
        <form
          className="card form"
          onSubmit={(e) => {
            e.preventDefault()
            void run(() => api.setCompanyName(companyName))
          }}
        >
          <h2>Company name</h2>
          <label>
            Name
            <input
              value={companyName}
              maxLength={120}
              placeholder="Issue Tracker"
              onChange={(e) => setCompanyName(e.target.value)}
            />
          </label>
          <p className="muted">Leave it empty to go back to the default title.</p>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={busy}>
              Save name
            </button>
          </div>
        </form>

        <div className="card">
          <h2>Logo</h2>
          <div className="logo-preview">
            {branding.hasLogo ? (
              <img src={api.logoUrl(branding.logoVersion)} alt="Current logo" />
            ) : (
              <span className="muted">No logo set — the default mark is used.</span>
            )}
          </div>
          <p className="muted">PNG, JPEG, GIF, WebP or SVG, up to 512 KB.</p>

          <input
            ref={fileInput}
            type="file"
            accept={ACCEPT}
            className="visually-hidden"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) void run(() => api.setLogo(file))
              if (fileInput.current) fileInput.current.value = ''
            }}
          />
          <div className="form-actions">
            {branding.hasLogo && (
              <button
                className="btn btn-ghost btn-danger"
                disabled={busy}
                onClick={() => void run(() => api.clearLogo())}
              >
                Remove logo
              </button>
            )}
            <button
              className="btn btn-primary"
              disabled={busy}
              onClick={() => fileInput.current?.click()}
            >
              {branding.hasLogo ? 'Replace logo' : 'Upload logo'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
