/** The app logo — same glyph as the favicon, so the tab and the header agree. */
export default function BrandMark({ size = 28 }: { size?: number }) {
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
