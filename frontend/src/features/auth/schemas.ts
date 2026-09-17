import { z } from 'zod'

/**
 * Client-side validation for the authentication forms.
 *
 * Every rule here mirrors a Jakarta constraint on the matching record in the
 * Spring `auth.dto` package, so the browser refuses exactly what the server
 * would refuse and nothing more. Anything stricter would reject input the API
 * accepts; anything looser would trade an instant message for a round trip.
 *
 * This is a convenience, not a control. The server validates again regardless,
 * and its `VALIDATION_ERROR` body is mapped back onto the offending fields.
 */

/** `@Size(max = 254)`, the longest address RFC 5321 permits. */
const EMAIL_MAX = 254
/** `app.security.password.min-length`, also `@Size(min = 8)` on the records. */
const PASSWORD_MIN = 8
/** `@Size(max = 200)`. bcrypt truncates at 72 bytes; the record bounds it earlier. */
const PASSWORD_MAX = 200
/** `@Size(max = 80)` on `firstName` and `lastName`. */
const NAME_MAX = 80

const emailFormat = z.email()

/**
 * Presence and length first, shape second.
 *
 * The shape check passes an empty value through rather than failing it. A
 * refinement runs even when an earlier check on the same field has already
 * failed, so without this an empty box would collect two messages and be told
 * both that it is required and that it is not a valid address.
 */
const email = z
  .string()
  .trim()
  .min(1, 'Enter your email address.')
  .max(EMAIL_MAX, `An email address cannot be longer than ${EMAIL_MAX} characters.`)
  .refine(
    (value) => value === '' || emailFormat.safeParse(value).success,
    'Enter a valid email address.',
  )

const newPassword = z
  .string()
  .min(PASSWORD_MIN, `Use at least ${PASSWORD_MIN} characters.`)
  .max(PASSWORD_MAX, `A password cannot be longer than ${PASSWORD_MAX} characters.`)

const personName = (label: string) =>
  z
    .string()
    .trim()
    .min(1, `Enter your ${label}.`)
    .max(NAME_MAX, `A ${label} cannot be longer than ${NAME_MAX} characters.`)

/**
 * Sign-in.
 *
 * The address is checked for presence and length but not for shape, which is
 * what `LoginRequest` does and for the reason its javadoc gives: a malformed
 * address has no account either, so the rejection must read the same both ways
 * rather than confirm that one of the two halves was at least well formed.
 */
export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address.').max(EMAIL_MAX),
  password: z.string().min(1, 'Enter your password.').max(PASSWORD_MAX),
})

/** Requesting a reset message, and asking for the verification mail again. */
export const emailOnlySchema = z.object({ email })

const mismatch = { message: 'The passwords do not match.', path: ['confirmPassword'] }

/**
 * Creating an account. Mirrors `RegisterRequest`, plus a confirmation field.
 *
 * The API has no `confirmPassword`. It exists because a typo on the one form
 * whose value is never echoed back locks somebody out of a brand new account,
 * and it is dropped before the request is built.
 */
export const registerSchema = z
  .object({
    firstName: personName('first name'),
    lastName: personName('last name'),
    email,
    password: newPassword,
    confirmPassword: z.string().min(1, 'Confirm your password.'),
  })
  .refine((values) => values.password === values.confirmPassword, mismatch)

/** Choosing a new password from a reset link. Mirrors `ResetPasswordRequest`. */
export const resetPasswordSchema = z
  .object({
    newPassword,
    confirmPassword: z.string().min(1, 'Confirm your password.'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, mismatch)

/**
 * Changing a password from inside a session.
 *
 * Mirrors `ChangePasswordRequest`: the current password is required and bounded
 * only by length, because an old password that no longer meets today's rules is
 * still the right answer to "what is it now". The new one gets the full check.
 *
 * The refinement is this form's own. Re-entering the password you already have
 * is not an error the backend refuses, but it is never what somebody meant.
 */
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password.').max(PASSWORD_MAX),
    newPassword,
    confirmPassword: z.string().min(1, 'Confirm your new password.'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: 'The passwords do not match.',
    path: ['confirmPassword'],
  })
  .refine((values) => values.currentPassword !== values.newPassword, {
    message: 'The new password must be different from the current one.',
    path: ['newPassword'],
  })

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
export type EmailOnlyValues = z.infer<typeof emailOnlySchema>
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>
