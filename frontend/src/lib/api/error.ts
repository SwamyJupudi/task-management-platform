import type { ApiErrorBody, FieldViolation } from './types'

/**
 * A failed request, carrying the backend's own error body when there was one.
 *
 * The backend has already decided what is safe to show a user, so `message`
 * is displayable as-is. `requestId` is the key support needs to find the
 * internal detail in the logs, which is why it is kept rather than discarded.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId: string | undefined
  readonly violations: FieldViolation[]
  readonly body: ApiErrorBody | undefined

  constructor(args: {
    status: number
    code: string
    message: string
    requestId?: string | undefined
    violations?: FieldViolation[]
    body?: ApiErrorBody | undefined
  }) {
    super(args.message)
    this.name = 'ApiError'
    this.status = args.status
    this.code = args.code
    this.requestId = args.requestId
    this.violations = args.violations ?? []
    this.body = args.body
  }

  /** The session is gone or was never established. Triggers a refresh, then a sign-out. */
  get isUnauthorised(): boolean {
    return this.status === 401
  }

  /** Authenticated, but not permitted. Never worth retrying. */
  get isForbidden(): boolean {
    return this.status === 403
  }

  get isNotFound(): boolean {
    return this.status === 404
  }

  /** Input the user can correct; `violations` says which fields. */
  get isValidation(): boolean {
    return this.status === 422 || this.code === 'VALIDATION_ERROR'
  }

  /** A fault on the server side, as opposed to a bad request. */
  get isServerFault(): boolean {
    return this.status >= 500
  }

  /** Maps the violation list to the shape a form library wants. */
  fieldErrors(): Record<string, string> {
    const result: Record<string, string> = {}
    for (const violation of this.violations) {
      result[violation.field] ??= violation.message
    }
    return result
  }
}

/** The network never reached the server, or the request was aborted. */
export class NetworkError extends Error {
  readonly cause: unknown

  constructor(message: string, cause?: unknown) {
    super(message)
    this.name = 'NetworkError'
    this.cause = cause
  }
}

/**
 * The message to put in front of a user for any thrown value.
 *
 * The requirements are explicit that internal errors are never exposed, so
 * anything that is not a deliberate `ApiError` collapses to one generic line.
 */
export function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof NetworkError) {
    return 'We could not reach the server. Check your connection and try again.'
  }
  return 'Something went wrong. Please try again.'
}
