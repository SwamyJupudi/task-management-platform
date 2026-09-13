import { parseBody } from '../mentions'
import type { Mention } from '../types'

/**
 * A comment's words, with the people in it named.
 *
 * The body is plain text and is rendered as text: every segment goes into a
 * React text node, so nothing in it can become markup. The backend says the
 * same thing on the field — "plain text, never HTML" — and this is the half of
 * that contract the client is responsible for.
 *
 * Whitespace is preserved rather than collapsed, because somebody who typed
 * paragraphs meant them.
 */
export function CommentBody({ body, mentions }: { body: string; mentions: Mention[] }) {
  const segments = parseBody(body, mentions)

  return (
    <p className="text-sm whitespace-pre-wrap">
      {segments.map((segment, index) =>
        segment.kind === 'mention' ? (
          <span
            key={`${segment.userId}-${index}`}
            className="rounded bg-primary/10 px-1 font-medium text-foreground"
          >
            @{segment.label}
          </span>
        ) : (
          <span key={`text-${index}`}>{segment.text}</span>
        ),
      )}
    </p>
  )
}
