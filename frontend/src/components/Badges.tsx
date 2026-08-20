import type { TicketPriority, TicketType } from '../api/types'

const TYPE_GLYPHS: Record<TicketType, { icon: string; className: string }> = {
  STORY: { icon: '◆', className: 'type-story' },
  TASK: { icon: '✓', className: 'type-task' },
  BUG: { icon: '●', className: 'type-bug' },
  EPIC: { icon: '⬟', className: 'type-epic' },
}

const PRIORITY_GLYPHS: Record<TicketPriority, { icon: string; className: string }> = {
  HIGHEST: { icon: '⯅⯅', className: 'prio-highest' },
  HIGH: { icon: '⯅', className: 'prio-high' },
  MEDIUM: { icon: '＝', className: 'prio-medium' },
  LOW: { icon: '⯆', className: 'prio-low' },
  LOWEST: { icon: '⯆⯆', className: 'prio-lowest' },
}

export function TypeBadge({ type }: { type: TicketType }) {
  const glyph = TYPE_GLYPHS[type]
  return (
    <span className={`glyph ${glyph.className}`} title={type}>
      {glyph.icon}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  const glyph = PRIORITY_GLYPHS[priority]
  return (
    <span className={`glyph ${glyph.className}`} title={`Priority: ${priority}`}>
      {glyph.icon}
    </span>
  )
}
