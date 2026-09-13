import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { ApiError } from '@/lib/api'

/**
 * Puts a rejected request's field messages back on the fields that caused them.
 *
 * The backend answers a `VALIDATION_ERROR` with an `errors` array naming each
 * field, and those names match the form's, because both mirror the same record.
 * Anything it names that the form does not have is left alone, for the summary
 * at the top of the form to show.
 *
 * Lives in `lib` rather than in a feature because every form in the application
 * needs it and none of them should have to import from another feature to get
 * it. The authentication feature has its own copy from before this existed and
 * can be pointed here whenever that code is next touched.
 */
export function applyFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): void {
  if (!(error instanceof ApiError)) return

  const violations = error.fieldErrors()
  for (const field of fields) {
    const message = violations[field]
    if (message) setError(field, { type: 'server', message })
  }
}
