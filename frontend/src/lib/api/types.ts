/**
 * The wire shapes the backend actually returns.
 *
 * These mirror `ApiErrorResponse` and `PageResponse` in the Spring codebase.
 * They are hand-written for now and deliberately small; the plan recorded in
 * docs/architecture.md is to generate the feature DTOs from the OpenAPI
 * document at /v3/api-docs, at which point only these two envelopes stay
 * hand-written.
 */

/** One rejected input value. Present only on validation failures. */
export interface FieldViolation {
  field: string
  message: string
}

/** The single error body shape returned by every endpoint in the platform. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  /** Stable machine-readable code, e.g. `VALIDATION_ERROR`. */
  code: string
  /** Safe message, already written for display to the user. */
  message: string
  path: string
  /** Correlates this response with the server logs. */
  requestId: string
  errors?: FieldViolation[]
}

/** The one paginated body shape returned by every list endpoint. */
export interface Page<T> {
  content: T[]
  /** Zero-based. */
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** The query parameters every list endpoint accepts. */
export interface PageRequest {
  page?: number
  size?: number
  /** `field,direction`, e.g. `createdAt,desc`. */
  sort?: string
}
