import { useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Attachment } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../format'
import Avatar from './Avatar'

/** Mirrors the server's allow-list; the picker should not offer what upload would refuse. */
const ACCEPT = [
  '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
  '.txt', '.md', '.csv', '.json',
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.zip',
].join(',')

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function Attachments({
  ticketKey,
  archived,
}: {
  ticketKey: string
  archived: boolean
}) {
  const { user } = useAuth()
  const fileInput = useRef<HTMLInputElement>(null)

  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .listAttachments(ticketKey)
      .then((result) => !cancelled && setAttachments(result))
      .catch((err) =>
        !cancelled && setError(err instanceof ApiError ? err.message : 'Failed to load attachments'),
      )
    return () => {
      cancelled = true
    }
  }, [ticketKey])

  async function upload(files: FileList | null) {
    if (!files?.length) return
    setBusy(true)
    setError(null)
    try {
      // Sequential rather than parallel: the per-ticket cap is checked server-side per
      // upload, and one rejected file should not cancel the rest.
      for (const file of Array.from(files)) {
        try {
          const created = await api.uploadAttachment(ticketKey, file)
          setAttachments((prev) => [created, ...prev])
        } catch (err) {
          setError(
            err instanceof ApiError ? `${file.name}: ${err.message}` : `Could not upload ${file.name}`,
          )
        }
      }
    } finally {
      setBusy(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  async function download(attachment: Attachment) {
    try {
      await api.downloadAttachment(attachment.id, attachment.filename)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not download that file')
    }
  }

  async function remove(attachment: Attachment) {
    if (!confirm(`Remove ${attachment.filename}?`)) return
    try {
      await api.deleteAttachment(attachment.id)
      setAttachments((prev) => prev.filter((a) => a.id !== attachment.id))
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove that file')
    }
  }

  return (
    <section className="attachments">
      <div className="links-head">
        <h2>Attachments ({attachments.length})</h2>
        {!archived && (
          <>
            <input
              ref={fileInput}
              type="file"
              multiple
              accept={ACCEPT}
              className="visually-hidden"
              onChange={(e) => upload(e.target.files)}
            />
            <button
              className="btn btn-ghost"
              disabled={busy}
              onClick={() => fileInput.current?.click()}
            >
              {busy ? 'Uploading…' : 'Attach files'}
            </button>
          </>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      {attachments.length === 0 ? (
        <p className="muted">
          No documents attached{archived ? '.' : ' — PDFs, Office documents, images and archives are accepted.'}
        </p>
      ) : (
        <ul className="attachment-list">
          {attachments.map((attachment) => (
            <li key={attachment.id} className="attachment-row">
              <Avatar user={attachment.uploadedBy} size={26} />
              <div className="attachment-meta">
                <button className="link-button attachment-name" onClick={() => download(attachment)}>
                  {attachment.filename}
                </button>
                <span className="muted">
                  {formatSize(attachment.sizeBytes)} · {attachment.uploadedBy.displayName} ·{' '}
                  {formatDateTime(attachment.uploadedAt)}
                </span>
              </div>
              <span className="spacer" />
              {!archived && (user?.id === attachment.uploadedBy.id || user?.role === 'ADMIN') && (
                <button className="link-button" onClick={() => remove(attachment)}>
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
