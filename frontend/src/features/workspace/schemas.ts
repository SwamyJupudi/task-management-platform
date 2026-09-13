import { z } from 'zod'

/**
 * Client-side validation for the workspace settings form.
 *
 * Mirrors the Jakarta constraints on `UpdateWorkspaceRequest`. The server
 * validates again and its message comes back on the field that caused it.
 *
 * There is no slug field. The backend offers none: a slug appears in links that
 * have already been shared, so it is fixed once the workspace exists.
 */

/** `@Size(min = 1, max = 120)` on the name. */
const NAME_MAX = 120

/** `@Size(max = 500)`. Blank is legal and is the one way to clear it. */
const DESCRIPTION_MAX = 500

/** `@Size(max = 64)`. The value itself is checked against the JVM zone database. */
const TIMEZONE_MAX = 64

export const workspaceSettingsSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'A workspace needs a name.')
    .max(NAME_MAX, `A name cannot be longer than ${NAME_MAX} characters.`),
  description: z
    .string()
    .trim()
    .max(DESCRIPTION_MAX, `A description cannot be longer than ${DESCRIPTION_MAX} characters.`),
  timezone: z
    .string()
    .trim()
    .min(1, 'Choose a time zone.')
    .max(TIMEZONE_MAX, `A time zone name cannot be longer than ${TIMEZONE_MAX} characters.`),
  defaultRoleSlug: z.string(),
})

export type WorkspaceSettingsValues = z.infer<typeof workspaceSettingsSchema>
