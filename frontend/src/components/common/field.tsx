import { useId, type ComponentProps, type ReactNode } from 'react'
import type { FieldError } from 'react-hook-form'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/**
 * A labelled input with its validation message, wired for accessibility.
 *
 * One component so every field announces itself the same way: the label is
 * associated by id, `aria-invalid` marks a rejected value, and
 * `aria-describedby` points a screen reader at whichever of the hint and the
 * error is actually rendered. The message is `role="alert"`, so it is read when
 * it appears rather than only on the next focus.
 *
 * It takes a `FieldError` rather than a form instance, so it stays a plain
 * controlled input that React Hook Form's `register` spreads onto.
 */

interface FieldProps extends Omit<ComponentProps<'input'>, 'id'> {
  label: string
  error?: FieldError | undefined
  /** Guidance shown under the field while it is valid. */
  hint?: string
  /** Rendered to the right of the label. */
  action?: ReactNode
}

export function Field({ label, error, hint, action, className, ...props }: FieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const hintId = `${id}-hint`

  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ')

  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2">
        <Label htmlFor={id}>{label}</Label>
        {action}
      </div>
      <Input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={cn('h-9', className)}
        {...props}
      />
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-destructive">
          {error.message}
        </p>
      ) : hint ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
