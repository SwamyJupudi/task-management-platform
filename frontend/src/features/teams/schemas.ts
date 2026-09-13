import { z } from 'zod'

/**
 * Client-side validation for the team form.
 *
 * Mirrors the Jakarta constraints on `CreateTeamRequest` and
 * `UpdateTeamRequest`, so the browser refuses exactly what the server would.
 * The server validates again and its message comes back on the field.
 */

/** `@NotBlank @Size(max = 120)`. */
const NAME_MAX = 120
/** `@Size(max = 500)`. */
const DESCRIPTION_MAX = 500

export const teamSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name.')
    .max(NAME_MAX, `A name cannot be longer than ${NAME_MAX} characters.`),
  description: z
    .string()
    .trim()
    .max(DESCRIPTION_MAX, `A description cannot be longer than ${DESCRIPTION_MAX} characters.`),
  /** Create only; empty means the team starts without a lead. */
  leadUserId: z.string(),
})

export type TeamValues = z.infer<typeof teamSchema>
