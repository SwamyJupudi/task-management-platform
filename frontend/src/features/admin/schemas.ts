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
