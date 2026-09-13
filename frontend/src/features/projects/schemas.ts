import { z } from 'zod'

/**
 * Client-side validation for the project forms.
 *
 * Every rule mirrors a Jakarta constraint on the matching record in the Spring
 * `projects.dto` package, so the browser refuses exactly what the server would
 * refuse and nothing more. Anything stricter would reject input the API
 * accepts; anything looser would trade an instant message for a round trip.
 *
 * This is a convenience, not a control. The server validates again regardless,
 * and its `VALIDATION_ERROR` body is mapped back onto the offending fields.
 */

/** `@Size(max = 10)` and `@Pattern(^[A-Za-z][A-Za-z0-9]{1,9}$)`. */
const KEY_MAX = 10
const NAME_MAX = 120
const DESCRIPTION_MAX = 2000

const keyPattern = /^[A-Za-z][A-Za-z0-9]{1,9}$/

/**
 * The project key.
 *
 * Presence first, shape second, and the shape check lets an empty value
 * through: a refinement runs even when an earlier check on the same field has
 * failed, so without that an empty box would be told both that it is required
 * and that it is malformed.
 */
const key = z
  .string()
  .trim()
  .min(1, 'Enter a key.')
  .max(KEY_MAX, `A key cannot be longer than ${KEY_MAX} characters.`)
  .refine(
    (value) => value === '' || keyPattern.test(value),
    'Use 2 to 10 letters and digits, starting with a letter.',
  )

const name = z
  .string()
  .trim()
  .min(1, 'Enter a name.')
  .max(NAME_MAX, `A name cannot be longer than ${NAME_MAX} characters.`)

const description = z
  .string()
  .trim()
  .max(DESCRIPTION_MAX, `A description cannot be longer than ${DESCRIPTION_MAX} characters.`)

/** `<input type="date">` gives an empty string or `yyyy-mm-dd`. */
const isoDate = z
  .string()
  .trim()
  .refine(
    (value) => value === '' || /^\d{4}-\d{2}-\d{2}$/.test(value),
    'Use the date picker, or clear the field.',
  )

/**
 * Free text that becomes the label set.
 *
 * Comma-separated because there is no endpoint that lists the workspace's
 * label catalog, so there is nothing to populate a picker from. The backend
 * adds unknown names to the catalog on write, which is what makes plain typing
 * the correct interaction here rather than a limitation.
 */
const labels = z.string().trim().max(500, 'That is too many labels.')

/**
 * Both dates are optional, but an end before a start is never what was meant.
 *
 * Checked here because the backend's own message for it arrives after a round
 * trip, and the fields are adjacent on the form.
 */
const orderedDates = <T extends { startDate: string; endDate: string }>(
  values: T,
  ctx: z.RefinementCtx,
) => {
  if (values.startDate !== '' && values.endDate !== '' && values.endDate < values.startDate) {
    ctx.addIssue({
      code: 'custom',
      path: ['endDate'],
      message: 'The end date cannot be before the start date.',
    })
  }
}

export const createProjectSchema = z
  .object({
    key,
    name,
    description,
    priority: z.string(),
    teamId: z.string(),
    ownerUserId: z.string(),
    startDate: isoDate,
    endDate: isoDate,
    labels,
  })
  .superRefine(orderedDates)

export type CreateProjectValues = z.infer<typeof createProjectSchema>

/**
 * The edit form. No key: it is immutable and appears in links.
 *
 * No status and no owner either, each for the reason the backend gives for
 * keeping them off `UpdateProjectRequest`: a transition is checked against the
 * state machine, and naming an owner also adjusts the roster. Both are their
 * own controls on the detail screen.
 */
export const editProjectSchema = z
  .object({
    name,
    description,
    priority: z.string(),
    teamId: z.string(),
    startDate: isoDate,
    endDate: isoDate,
    labels,
  })
  .superRefine(orderedDates)

export type EditProjectValues = z.infer<typeof editProjectSchema>

/** Splits the label box into the set the API expects, dropping blanks and duplicates. */
export function parseLabels(raw: string): string[] {
  const parts = raw
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part !== '')
  return [...new Set(parts)]
}
