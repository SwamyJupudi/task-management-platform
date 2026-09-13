import { z } from 'zod'

/**
 * Client-side validation for the task forms.
 *
 * Every rule mirrors a Jakarta constraint on the matching record in the Spring
 * `tasks.dto` package, so the browser refuses exactly what the server would
 * refuse and nothing more. Anything stricter would reject input the API
 * accepts; anything looser would trade an instant message for a round trip.
 *
 * This is a convenience, not a control. The server validates again regardless,
 * and its `VALIDATION_ERROR` body is mapped back onto the offending fields.
 */

/** `@NotBlank @Size(max = 200)`. */
const TITLE_MAX = 200
/** `@Size(max = 10000)`. */
const DESCRIPTION_MAX = 10_000

const title = z
  .string()
  .trim()
  .min(1, 'Enter a title.')
  .max(TITLE_MAX, `A title cannot be longer than ${TITLE_MAX} characters.`)

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
 * Effort, in minutes. `@Min(0)` and an integer on the record.
 *
 * Kept as a string in the form because a number input hands back `NaN` for an
 * empty box, and an empty box means "not recorded" rather than zero.
 */
const minutes = z
  .string()
  .trim()
  .refine((value) => value === '' || /^\d+$/.test(value), 'Enter a whole number of minutes.')
  .refine((value) => value === '' || Number(value) <= 10_000_000, 'That is too large.')

const labels = z.string().trim().max(500, 'That is too many labels.')

/** An end before a start is never what was meant, and both fields are adjacent. */
const orderedDates = <T extends { startDate: string; dueDate: string }>(
  values: T,
  ctx: z.RefinementCtx,
) => {
  if (values.startDate !== '' && values.dueDate !== '' && values.dueDate < values.startDate) {
    ctx.addIssue({
      code: 'custom',
      path: ['dueDate'],
      message: 'The due date cannot be before the start date.',
    })
  }
}

export const createTaskSchema = z
  .object({
    projectId: z.string().min(1, 'Choose a project.'),
    title,
    description,
    priority: z.string(),
    assigneeUserId: z.string(),
    startDate: isoDate,
    dueDate: isoDate,
    estimatedMinutes: minutes,
    labels,
  })
  .superRefine(orderedDates)

export type CreateTaskValues = z.infer<typeof createTaskSchema>

/**
 * The edit form.
 *
 * No project: a task is numbered against its project and moving it between
 * projects is not something the API supports. No status and no assignee
 * either, for the reason the backend gives for keeping them off
 * `UpdateTaskRequest` — each has its own endpoint and its own permission.
 */
export const editTaskSchema = z
  .object({
    title,
    description,
    priority: z.string(),
    startDate: isoDate,
    dueDate: isoDate,
    estimatedMinutes: minutes,
    actualMinutes: minutes,
    labels,
  })
  .superRefine(orderedDates)

export type EditTaskValues = z.infer<typeof editTaskSchema>

/** Splits the label box into the set the API expects, dropping blanks and duplicates. */
export function parseLabels(raw: string): string[] {
  const parts = raw
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part !== '')
  return [...new Set(parts)]
}

/** An empty effort box means "not recorded", which is not the same as zero. */
export function parseMinutes(raw: string): number | undefined {
  return raw.trim() === '' ? undefined : Number(raw)
}
