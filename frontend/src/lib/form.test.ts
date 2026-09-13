import { ApiError } from '@/lib/api'

import { applyFieldErrors } from './form'

/**
 * Putting a rejected request's field messages back on the fields that caused
 * them.
 *
 * The contract is narrow and worth holding: only fields the caller named are
 * touched, so a violation the form has no input for falls through to the
 * summary banner instead of disappearing.
 */

function validationError(violations: { field: string; message: string }[]): ApiError {
  return new ApiError({
    status: 400,
    code: 'VALIDATION_ERROR',
    message: 'Some of the submitted values are not valid.',
    requestId: 'req-1',
    violations,
  })
}

describe('applyFieldErrors', () => {
  it('sets a message on a field the form declared', () => {
    const setError = vi.fn()

    applyFieldErrors(validationError([{ field: 'slug', message: 'must be lowercase' }]), setError, [
      'slug',
    ])

    expect(setError).toHaveBeenCalledWith('slug', {
      type: 'server',
      message: 'must be lowercase',
    })
  })

  it('sets each of several', () => {
    const setError = vi.fn()

    applyFieldErrors(
      validationError([
        { field: 'name', message: 'required' },
        { field: 'slug', message: 'malformed' },
      ]),
      setError,
      ['name', 'slug'],
    )

    expect(setError).toHaveBeenCalledTimes(2)
  })

  it('ignores a violation naming a field the form does not have', () => {
    // It belongs to the banner, which shows the summary message.
    const setError = vi.fn()

    applyFieldErrors(
      validationError([{ field: 'token', message: 'must not be blank' }]),
      setError,
      ['name'],
    )

    expect(setError).not.toHaveBeenCalled()
  })

  it('does nothing for an error that is not an ApiError', () => {
    const setError = vi.fn()

    applyFieldErrors(new Error('offline'), setError, ['name'])
    applyFieldErrors(undefined, setError, ['name'])

    expect(setError).not.toHaveBeenCalled()
  })

  it('does nothing for a failure that carried no violations', () => {
    // A 409 or a plain 400 names no field; the banner reports it.
    const setError = vi.fn()

    applyFieldErrors(
      new ApiError({
        status: 409,
        code: 'CONFLICT',
        message: 'A workspace with that address already exists.',
        requestId: 'req-2',
        violations: [],
      }),
      setError,
      ['slug'],
    )

    expect(setError).not.toHaveBeenCalled()
  })
})
