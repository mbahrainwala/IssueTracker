import { useEffect, useState } from 'react'
import { authHeaders } from '../api/client'

/**
 * An image behind an authorised endpoint.
 *
 * A project picture is project data, so its endpoint checks membership - which means the
 * browser cannot fetch it through a plain `src`, because that carries no bearer token. The
 * bytes are fetched instead and handed to the tag as an object URL, revoked on unmount so a
 * long list of tiles does not leak one blob per card.
 *
 * Renders nothing at all if the fetch fails: a missing picture is decoration, never an error
 * worth showing in the middle of a project tile.
 */
export default function AuthImage({
  url,
  alt,
  className,
}: {
  url: string
  alt: string
  className?: string
}) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null)

  useEffect(() => {
    let revoked = false
    let created: string | null = null

    fetch(url, { headers: authHeaders() })
      .then((response) => (response.ok ? response.blob() : Promise.reject(response.status)))
      .then((blob) => {
        if (revoked) return
        created = URL.createObjectURL(blob)
        setObjectUrl(created)
      })
      .catch(() => setObjectUrl(null))

    return () => {
      revoked = true
      if (created) URL.revokeObjectURL(created)
    }
  }, [url])

  if (!objectUrl) return null
  return <img className={className} src={objectUrl} alt={alt} />
}
