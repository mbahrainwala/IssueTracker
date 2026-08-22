import { api } from '../api/client'
import { useBranding } from '../branding/BrandingContext'

/**
 * The company logo when one has been uploaded, otherwise the app's own glyph — the same one
 * as the favicon, so the tab and the header agree.
 */
export default function BrandMark({ size = 28 }: { size?: number }) {
  const { branding } = useBranding()

  if (branding.hasLogo) {
    return (
      <img
        className="brand-logo"
        src={api.logoUrl(branding.logoVersion)}
        alt={branding.companyName ?? 'Company logo'}
        height={size}
        style={{ maxHeight: size }}
      />
    )
  }

  return (
    <svg
      className="brand-mark"
      viewBox="0 0 64 64"
      width={size}
      height={size}
      role="img"
      aria-label="Issue Tracker"
    >
      <rect width="64" height="64" rx="14.08" fill="#0052cc" />
      <g fill="#ffffff">
        <rect x="13.76" y="15.04" width="8" height="34.56" rx="4" />
        <rect x="28" y="15.04" width="8" height="19.84" rx="4" />
        <rect x="42.24" y="15.04" width="8" height="27.52" rx="4" />
      </g>
    </svg>
  )
}

/** The title beside the mark: the company's name if set, otherwise the app's. */
export function BrandName() {
  const { branding } = useBranding()
  return <>{branding.companyName ?? 'Issue Tracker'}</>
}
