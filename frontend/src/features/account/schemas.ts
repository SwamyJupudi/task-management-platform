import { z } from 'zod'

/**
 * Client-side validation for the profile form.
 *
 * Mirrors the Jakarta constraints on `UpdateProfileRequest`: `@NotBlank
 * @Size(max = 80)` on both names. The server validates again and its message
 * comes back on the field that caused it.
 *
 * The password form's rules live in the authentication feature's schemas,
 * beside the ones registration and reset use, so there is one definition of
 * what a password has to be.
 */

/** `@Size(max = 80)` on both. */
const NAME_MAX = 80

export const profileSchema = z.object({
  firstName: z
    .string()
    .trim()
    .min(1, 'Enter your first name.')
    .max(NAME_MAX, `A first name cannot be longer than ${NAME_MAX} characters.`),
  lastName: z
    .string()
    .trim()
    .min(1, 'Enter your last name.')
    .max(NAME_MAX, `A last name cannot be longer than ${NAME_MAX} characters.`),
})

export type ProfileValues = z.infer<typeof profileSchema>
