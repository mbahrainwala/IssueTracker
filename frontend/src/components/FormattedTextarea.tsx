import { useRef, useState, type KeyboardEvent, type TextareaHTMLAttributes } from 'react'

/** Just enough of a user to offer and to insert. */
export interface MentionCandidate {
  id: number
  username: string
  displayName: string
}

/**
 * The half-typed mention immediately before the caret: "@" plus whatever has been typed since,
 * which may be nothing. Anchored to the end of the text-so-far, and refusing a preceding word
 * character for the same reason the renderer does - an email address is not a mention.
 */
export const MENTION_TOKEN = /(?:^|[^\w@])@([A-Za-z0-9._-]*)$/

/** Enough names to choose from without covering the field being typed into. */
const MAX_SUGGESTIONS = 6

/**
 * The three marks, with the shortcut each answers to. A mark is a run of {@code width}
 * repetitions of {@code char}: bold and italic share the asterisk and differ only in how
 * many of them, which is exactly why the run has to be counted rather than string-matched.
 */
const CONTROLS = [
  { key: 'b', char: '*', width: 2, label: 'B', title: 'Bold (Ctrl+B)', className: 'fmt-bold' },
  { key: 'i', char: '*', width: 1, label: 'I', title: 'Italic (Ctrl+I)', className: 'fmt-italic' },
  { key: 'u', char: '_', width: 2, label: 'U', title: 'Underline (Ctrl+U)', className: 'fmt-underline' },
] as const

type Control = (typeof CONTROLS)[number]

/**
 * Whether a run of that many marker characters already has this mark switched on.
 *
 * One asterisk is italic, two are bold, and three are both - so italic is on whenever the
 * run is odd, and bold whenever there are at least two to spare. Reading the run this way is
 * what lets bold and italic be applied to the same text: a plain "does it end with `*`" test
 * sees the second asterisk of a bold pair and wrongly strips it.
 */
function isActive(control: Control, run: number): boolean {
  return control.width === 1 ? run % 2 === 1 : run >= control.width
}

/** Every character that can act as a marker, for stepping over a mark that is not ours. */
const MARK_CHARS: Set<string> = new Set(CONTROLS.map((c) => c.char))

/** How many of `char` sit in an unbroken run ending at `index` (exclusive). */
function runBefore(text: string, index: number, char: string): number {
  let count = 0
  while (index - count > 0 && text[index - count - 1] === char) count++
  return count
}

/** How many of `char` sit in an unbroken run starting at `index`. */
function runAfter(text: string, index: number, char: string): number {
  let count = 0
  while (index + count < text.length && text[index + count] === char) count++
  return count
}

type Props = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'value' | 'onChange'> & {
  value: string
  onChange: (value: string) => void
  /**
   * People who can be offered after an "@". Passed in rather than fetched here: every caller
   * already has the project's members loaded, and mentioning someone who cannot see the
   * project does nothing, so the members list is exactly the right set to suggest.
   */
  people?: MentionCandidate[]
}

/**
 * A textarea that writes the inline markers RichText renders, driven by the shortcuts people
 * already have in their fingers - Ctrl/Cmd+B, +I, +U - with a toolbar so the feature is
 * discoverable without knowing the syntax.
 *
 * Deliberately a plain textarea over a contenteditable: the value stays the exact string that
 * is stored and re-rendered, so what is typed, what is saved and what is displayed cannot
 * drift apart, and no HTML is ever produced that would then need sanitising.
 */
export default function FormattedTextarea({ value, onChange, people = [], ...props }: Props) {
  const ref = useRef<HTMLTextAreaElement>(null)

  /** The "@..." being typed, or null when the caret is not in one. */
  const [mentionQuery, setMentionQuery] = useState<string | null>(null)
  const [highlighted, setHighlighted] = useState(0)

  const suggestions =
    mentionQuery === null
      ? []
      : people
          .filter((person) => {
            const q = mentionQuery.toLowerCase()
            return (
              q === ''
              || person.username.toLowerCase().includes(q)
              || person.displayName.toLowerCase().includes(q)
            )
          })
          .slice(0, MAX_SUGGESTIONS)

  /** Recomputed whenever the text or the caret moves, since either can enter or leave a token. */
  function syncMentionQuery(el: HTMLTextAreaElement) {
    const upToCaret = el.value.slice(0, el.selectionStart)
    const match = MENTION_TOKEN.exec(upToCaret)
    setMentionQuery(match ? match[1]! : null)
    setHighlighted(0)
  }

  /** Replaces the half-typed "@..." with the chosen name, and leaves a trailing space. */
  function insertMention(person: MentionCandidate) {
    const el = ref.current
    if (el === null || mentionQuery === null) return

    const caret = el.selectionStart
    const tokenStart = caret - mentionQuery.length - 1
    const inserted = `@${person.username} `
    const next = value.slice(0, tokenStart) + inserted + value.slice(caret)

    onChange(next)
    setMentionQuery(null)
    const caretAfter = tokenStart + inserted.length
    requestAnimationFrame(() => {
      el.focus()
      el.setSelectionRange(caretAfter, caretAfter)
    })
  }

  /**
   * Turns one mark on or off around the selection, leaving any other mark on the same text
   * alone - so bold can be added to italic text and removed again without disturbing it.
   *
   * It works on the run of marker characters wrapping the selection rather than on the
   * literal markers: the run is measured, adjusted by this mark's width, and rewritten. With
   * nothing selected the markers are inserted and the caret parked between them.
   */
  function toggleMark(control: Control) {
    const el = ref.current
    if (!el) return

    let { selectionStart: selStart, selectionEnd: selEnd } = el

    // Markers the user included in the selection belong to the run, not to the text, so
    // hand them back before measuring - selecting "**word**" must behave like "word".
    while (
      selEnd - selStart >= 2 &&
      value[selStart] === control.char &&
      value[selEnd - 1] === control.char
    ) {
      selStart++
      selEnd--
    }

    // A different mark already wrapping this text sits between the selection and our own
    // markers, so step outward over it - otherwise bolding the "text" of "**__text__**"
    // measures a run of zero and nests a second bold inside the underline.
    let start = selStart
    let end = selEnd
    while (
      start > 0 &&
      end < value.length &&
      value[start - 1] === value[end] &&
      value[start - 1] !== control.char &&
      MARK_CHARS.has(value[start - 1])
    ) {
      start--
      end++
    }

    const run = Math.min(
      runBefore(value, start, control.char),
      runAfter(value, end, control.char),
    )
    const nextRun = isActive(control, run) ? run - control.width : run + control.width

    const markers = control.char.repeat(nextRun)
    const next =
      value.slice(0, start - run) + markers + value.slice(start, end) + markers + value.slice(end + run)

    // Markers are added or removed outside the selection, so it simply slides by the delta.
    const shift = nextRun - run
    onChange(next)
    // The value is controlled, so the selection has to be restored after React repaints it.
    requestAnimationFrame(() => {
      el.focus()
      el.setSelectionRange(selStart + shift, selEnd + shift)
    })
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // The picker takes these keys only while it is open, so Enter still makes a newline and
    // Tab still moves on whenever nobody is choosing a name.
    if (suggestions.length > 0) {
      if (event.key === 'ArrowDown') {
        event.preventDefault()
        setHighlighted((i) => (i + 1) % suggestions.length)
        return
      }
      if (event.key === 'ArrowUp') {
        event.preventDefault()
        setHighlighted((i) => (i - 1 + suggestions.length) % suggestions.length)
        return
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault()
        insertMention(suggestions[highlighted] ?? suggestions[0]!)
        return
      }
      if (event.key === 'Escape') {
        event.preventDefault()
        setMentionQuery(null)
        return
      }
    }

    if (!event.ctrlKey && !event.metaKey) return
    const control = CONTROLS.find((c) => c.key === event.key.toLowerCase())
    if (!control) return
    // Ctrl+U is "view source" in some browsers and Ctrl+B a bookmark sidebar; the field has
    // focus and a real use for the chord, so it is claimed here.
    event.preventDefault()
    toggleMark(control)
  }

  return (
    <div className="formatted-input">
      <div className="format-toolbar">
        {CONTROLS.map((control) => (
          <button
            key={control.key}
            type="button"
            className={`format-button ${control.className}`}
            title={control.title}
            aria-label={control.title}
            // Keeps focus and the selection in the textarea, which a click would otherwise take.
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => toggleMark(control)}
          >
            {control.label}
          </button>
        ))}
      </div>
      <div className="mention-anchor">
        <textarea
          {...props}
          ref={ref}
          value={value}
          onChange={(e) => {
            onChange(e.target.value)
            syncMentionQuery(e.target)
          }}
          // The caret can move without the text changing, which enters or leaves a token.
          onKeyUp={(e) => syncMentionQuery(e.currentTarget)}
          onClick={(e) => syncMentionQuery(e.currentTarget)}
          // Left open, the list would hang over whatever is clicked next.
          onBlur={() => setMentionQuery(null)}
          onKeyDown={onKeyDown}
        />
        {suggestions.length > 0 && (
          <ul className="mention-suggestions" role="listbox">
            {suggestions.map((person, index) => (
              <li key={person.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={index === highlighted}
                  className={index === highlighted ? 'mention-option mention-option-on' : 'mention-option'}
                  // Blur would close the list before the click landed.
                  onMouseDown={(e) => e.preventDefault()}
                  onMouseEnter={() => setHighlighted(index)}
                  onClick={() => insertMention(person)}
                >
                  <strong>@{person.username}</strong>
                  <span className="muted">{person.displayName}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
