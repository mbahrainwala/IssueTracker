import type { LaneInput } from '../api/client'

/**
 * Edits a board as a list: rename, reorder, add, remove, and pick which lane starts work and
 * which finishes it.
 *
 * The order in the array *is* the order on the board, so moving a lane is just swapping two
 * entries — there is no separate position to keep in step. "Starting" and "finished" are
 * radio-like: setting one clears the other lanes, because a board has exactly one of each and
 * letting the user submit two only to be refused would be a worse way to learn that.
 */
export default function LaneEditor({
  lanes,
  onChange,
  disabled,
}: {
  lanes: LaneInput[]
  onChange: (lanes: LaneInput[]) => void
  disabled?: boolean
}) {
  function update(index: number, patch: Partial<LaneInput>) {
    onChange(lanes.map((lane, i) => (i === index ? { ...lane, ...patch } : lane)))
  }

  function setExclusive(index: number, field: 'initial' | 'done') {
    onChange(lanes.map((lane, i) => ({ ...lane, [field]: i === index })))
  }

  function move(index: number, delta: number) {
    const target = index + delta
    if (target < 0 || target >= lanes.length) return
    const next = [...lanes]
    ;[next[index], next[target]] = [next[target]!, next[index]!]
    onChange(next)
  }

  function remove(index: number) {
    onChange(lanes.filter((_, i) => i !== index))
  }

  function add() {
    // No id: the server reads that as a lane that did not exist before.
    onChange([...lanes, { id: null, name: '', initial: lanes.length === 0, done: false }])
  }

  return (
    <div className="lane-editor">
      <ol className="lane-list">
        {lanes.map((lane, index) => (
          <li key={index} className="lane-row">
            <span className="lane-handle" aria-hidden="true">
              {index + 1}
            </span>
            <input
              className="lane-name"
              value={lane.name}
              placeholder="Lane name"
              maxLength={60}
              disabled={disabled}
              onChange={(e) => update(index, { name: e.target.value })}
            />
            <label className="lane-flag" title="New tickets appear here">
              <input
                type="radio"
                name="initial-lane"
                checked={lane.initial}
                disabled={disabled}
                onChange={() => setExclusive(index, 'initial')}
              />
              Start
            </label>
            <label className="lane-flag" title="Tickets can only be archived from this lane">
              <input
                type="radio"
                name="done-lane"
                checked={lane.done}
                disabled={disabled}
                onChange={() => setExclusive(index, 'done')}
              />
              Finished
            </label>
            <span className="spacer" />
            <button
              type="button"
              className="link-button"
              disabled={disabled || index === 0}
              onClick={() => move(index, -1)}
              aria-label={`Move ${lane.name || 'lane'} left`}
            >
              ↑
            </button>
            <button
              type="button"
              className="link-button"
              disabled={disabled || index === lanes.length - 1}
              onClick={() => move(index, 1)}
              aria-label={`Move ${lane.name || 'lane'} right`}
            >
              ↓
            </button>
            <button
              type="button"
              className="link-button"
              disabled={disabled || lanes.length === 1}
              onClick={() => remove(index)}
            >
              Remove
            </button>
          </li>
        ))}
      </ol>
      <button type="button" className="btn btn-ghost" disabled={disabled} onClick={add}>
        Add lane
      </button>
    </div>
  )
}

/**
 * Turns lanes as the API returns them into the shape this editor works with, carrying the id
 * so an edited lane is recognised as the same lane rather than a replacement.
 */
export function toLaneInputs(
  lanes: { id?: number; name: string; initial: boolean; done: boolean }[],
): LaneInput[] {
  return lanes.map((lane) => ({
    id: lane.id ?? null,
    name: lane.name,
    initial: lane.initial,
    done: lane.done,
  }))
}
