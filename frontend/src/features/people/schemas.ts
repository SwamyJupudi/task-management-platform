import { z } from 'zod'

/**
 * Client-side validation for the invite form.
 *
 * Mirrors the Jakarta constraints on `InviteMemberRequest`: `@NotBlank
 * @Email @Size(max = 254)`. The server validates again and its message comes
 * back on the field that caused it.
 */

/** `@Size(max = 254)`, the longest address RFC 5321 permits. */
const EMAIL_MAX = 254

const emailFormat = z.email()

/**
 * Presence and length first, shape second.
 *
 * The shape check lets an empty value through: a refinement runs even when an
 * earlier check on the same field has failed, so without this an empty box
 * would be told both that it is required and that it is malformed.
 */
export const inviteSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Enter an email address.')
    .max(EMAIL_MAX, `An email address cannot be longer than ${EMAIL_MAX} characters.`)
    .refine(
      (value) => value === '' || emailFormat.safeParse(value).success,
      'That does not look like an email address.',
    ),
  /** Empty means the workspace's default role, which the backend applies. */
  roleSlug: z.string(),
})

export type InviteValues = z.infer<typeof inviteSchema>
