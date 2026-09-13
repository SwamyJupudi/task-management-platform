import { z } from 'zod'

/**
 * Client-side validation for the one form the admin panel has.
 *
 * Mirrors the Jakarta constraints on `AdminUpdateProfileRequest`: `@NotBlank
 * @Size(max = 80)` on both names. The server validates again and its message
 * comes back on the field that caused it.
 *
 * There is no address field, and that is the backend's rule rather than an
 * omission: an address is an account's identity, changing it needs
 * re-verification and a decision about live sessions, and an administrator
 * changing somebody's address without their knowledge is an account takeover
 * with extra steps.
 */

/** `@Size(max = 80)` on both. */
const NAME_MAX = 80

export const accountProfileSchema = z.object({
  firstName: z
    .string()
    .trim()
    .min(1, 'Enter a first name.')
    .max(NAME_MAX, `A first name cannot be longer than ${NAME_MAX} characters.`),
  lastName: z
    .string()
    .trim()
    .min(1, 'Enter a last name.')
    .max(NAME_MAX, `A last name cannot be longer than ${NAME_MAX} characters.`),
})

export type AccountProfileValues = z.infer<typeof accountProfileSchema>

/**
 * Creating a workspace.
 *
 * Mirrors `CreateWorkspaceRequest`: `@NotBlank @Size(max = 120)` on the name,
 * and on the slug `@Size(max = 60)` with the pattern
 * `^[a-z0-9]+(-[a-z0-9]+)*$`. The message here is the backend's own wording, so
 * the same rejection reads the same whichever side catches it.
 *
 * The slug is fixed at creation — no endpoint changes one afterwards — which is
 * why the form suggests one and still lets it be edited before the only moment
 * it can be chosen.
 */
const WORKSPACE_NAME_MAX = 120
const WORKSPACE_SLUG_MAX = 60

export const createWorkspaceSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name.')
    .max(WORKSPACE_NAME_MAX, `A name cannot be longer than ${WORKSPACE_NAME_MAX} characters.`),
  slug: z
    .string()
    .trim()
    .min(1, 'Enter an address.')
    .max(WORKSPACE_SLUG_MAX, `An address cannot be longer than ${WORKSPACE_SLUG_MAX} characters.`)
    .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'must be lowercase words separated by single hyphens'),
})

export type CreateWorkspaceValues = z.infer<typeof createWorkspaceSchema>

/**
 * A slug suggested from a name.
 *
 * Folds accents, drops anything the pattern does not allow, and collapses
 * runs of hyphens. Only a suggestion: the field stays editable, and the value
 * is validated by the same rule whether it was typed or generated.
 */
export function suggestSlug(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, WORKSPACE_SLUG_MAX)
    .replace(/-+$/, '')
}
