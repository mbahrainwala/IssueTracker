import type { User } from '../api/types'

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}

/** Deterministic hue per user so the same person keeps the same colour everywhere. */
function hue(seed: string): number {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) % 360
  return hash
}

export default function Avatar({ user, size = 28 }: { user: User | null; size?: number }) {
  if (!user) {
    return (
      <span className="avatar avatar-empty" style={{ width: size, height: size, fontSize: size * 0.4 }} title="Unassigned">
        –
      </span>
    )
  }
  const h = hue(user.username)
  return (
    <span
      className="avatar"
      title={`${user.displayName} (@${user.username})`}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.38,
        background: `hsl(${h} 55% 42%)`,
      }}
    >
      {initials(user.displayName)}
    </span>
  )
}
