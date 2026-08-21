import { useRef, type KeyboardEvent, type TextareaHTMLAttributes } from 'react'

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
export default function FormattedTextarea({ value, onChange, ...props }: Props) {
  const ref = useRef<HTMLTextAreaElement>(null)

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
      <textarea
        {...props}
        ref={ref}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
      />
    </div>
  )
}
