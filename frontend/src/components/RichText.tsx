import type { ReactNode } from 'react'

/**
 * Matches bare URLs and www-style hosts. Trailing punctuation is left out of the match so
 * "see https://example.com." does not swallow the full stop into the link.
 */
const URL_PATTERN = /\b(https?:\/\/[^\s<>()[\]]+|www\.[^\s<>()[\]]+)(?<![.,;:!?'"])/i

/**
 * A mention. Must match the server's pattern (MentionService.MENTION) or the two disagree
 * about what was written: the chip must not appear for something that raised no mention, and
 * the "not preceded by a word character" rule is what keeps an email address out of it.
 */
const MENTION_PATTERN = /(?<![\w@])@([A-Za-z0-9._-]{3,60})/

/**
 * The inline marks, longest marker first so "**bold**" is never read as an italic "*"
 * wrapping "*bold*". Underline has no established markdown spelling; "__" is free here
 * because bold already has one, and the pairing reads naturally next to it.
 *
 * Each pattern requires the marked run to start and end with a non-space, the same rule
 * markdown uses. Without it "2 * 3 * 4" reads as an italic " 3 ", and multiplication is
 * more common in a ticket than emphasis with a space tucked inside the markers.
 */
export const MARKS = [
  // Three asterisks are bold and italic at once. It has to be matched as its own case:
  // letting the "**" rule take it would leave a stray asterisk on each side.
  { marker: '***', pattern: /\*\*\*(?!\s)([\s\S]+?)(?<!\s)\*\*\*/, wrap: (c: ReactNode, k: string) => <strong key={k}><em>{c}</em></strong> },
  { marker: '**', pattern: /\*\*(?!\s)([\s\S]+?)(?<!\s)\*\*/, wrap: (c: ReactNode, k: string) => <strong key={k}>{c}</strong> },
  { marker: '__', pattern: /__(?!\s)([\s\S]+?)(?<!\s)__/, wrap: (c: ReactNode, k: string) => <u key={k}>{c}</u> },
  { marker: '*', pattern: /\*(?!\s)([^*\n]+?)(?<!\s)\*/, wrap: (c: ReactNode, k: string) => <em key={k}>{c}</em> },
] as const

/**
 * Renders user-written text - a ticket or project description, a comment - with its inline
 * formatting applied and any links made clickable.
 *
 * The markers are stored as ordinary characters in an ordinary text column, and the output
 * is built from React elements: at no point is a string handed to the DOM as HTML. So the
 * formatting is a rendering convention, not a document format, and markup someone types is
 * still shown rather than run.
 *
 * Links open in a new tab with rel="noopener noreferrer": without noopener the opened page
 * can reach back through window.opener and navigate this tab somewhere else. Only http and
 * https become links - "javascript:" and "data:" stay inert text.
 */
export default function RichText({ text, className }: { text?: string | null; className?: string }) {
  if (!text) return null
  return <span className={className}>{parse(text, 'r')}</span>
}

function parse(text: string, keyPrefix: string): ReactNode[] {
  const out: ReactNode[] = []
  let rest = text
  let consumed = 0

  while (rest.length > 0) {
    const next = earliestMatch(rest)
    if (!next) {
      out.push(rest)
      break
    }

    if (next.index > 0) {
      out.push(rest.slice(0, next.index))
    }
    const key = `${keyPrefix}-${consumed + next.index}`
    // Marked spans recurse so "**bold with *italic* inside**" nests; a URL and a mention are
    // both atomic - there is nothing inside them to format.
    if (next.mark) {
      out.push(next.mark.wrap(parse(next.inner, key), key))
    } else if (next.kind === 'mention') {
      out.push(
        <span key={key} className="mention">
          {next.inner}
        </span>,
      )
    } else {
      out.push(link(next.inner, key))
    }

    const step = next.index + next.length
    consumed += step
    rest = rest.slice(step)
  }
  return out
}

interface Match {
  index: number
  length: number
  inner: string
  mark?: (typeof MARKS)[number]
  kind?: 'url' | 'mention'
}

/**
 * The match that starts earliest wins, and MARKS order breaks a tie at the same position -
 * which is what keeps "**" from being read as a nested "*".
 */
function earliestMatch(text: string): Match | null {
  let best: Match | null = null

  for (const mark of MARKS) {
    const found = mark.pattern.exec(text)
    if (found && (best === null || found.index < best.index)) {
      best = { index: found.index, length: found[0].length, inner: found[1], mark }
    }
  }

  const url = URL_PATTERN.exec(text)
  if (url && (best === null || url.index < best.index)) {
    best = { index: url.index, length: url[0].length, inner: url[0], kind: 'url' }
  }

  const mention = MENTION_PATTERN.exec(text)
  if (mention && (best === null || mention.index < best.index)) {
    best = { index: mention.index, length: mention[0].length, inner: mention[0], kind: 'mention' }
  }
  return best
}

function link(match: string, key: string): ReactNode {
  const candidate = match.toLowerCase().startsWith('www.') ? `https://${match}` : match
  let href: string | null = null
  try {
    const url = new URL(candidate)
    href = url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    href = null
  }

  return href ? (
    <a key={key} href={href} target="_blank" rel="noopener noreferrer">
      {match}
    </a>
  ) : (
    match
  )
}
