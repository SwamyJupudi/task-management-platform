import { useId, useState, type ComponentProps } from 'react'
import type { FieldError } from 'react-hook-form'
import { EyeIcon, EyeOffIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/**
 * A labelled input with its validation message, wired for accessibility.
 *
 * One component so every field on every authentication screen announces itself
 * the same way: the label is associated by id, `aria-invalid` marks a rejected
 * value, and `aria-describedby` points a screen reader at whichever of the hint
 * and the error is actually rendered. The message is `role="alert"`, so it is
 * read when it appears rather than only on the next focus.
 *
 * It takes a `FieldError` rather than a form instance, so it stays a plain
 * controlled input that React Hook Form's `register` spreads onto.
 */

interface TextFieldProps extends Omit<ComponentProps<'input'>, 'id'> {
  label: string
  error?: FieldError | undefined
  /** Guidance shown under the field while it is valid. */
  hint?: string
  /** Rendered to the right of the label, usually a "forgot password" link. */
  action?: React.ReactNode
}

export function TextField({ label, error, hint, action, className, ...props }: TextFieldProps) {
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

/**
 * The same field with a reveal toggle.
 *
 * Worth the extra control: the rules the backend enforces are long enough that
 * typing one blind into a box that is never echoed back is the ordinary way to
 * get locked out of a new account. The toggle is a real `button` with an
 * `aria-label` that states the current action, and it is excluded from the tab
 * order so it does not sit between the password field and the submit button.
 */
export function PasswordField({ label, error, hint, action, className, ...props }: TextFieldProps) {
  const [revealed, setRevealed] = useState(false)
  const Icon = revealed ? EyeOffIcon : EyeIcon

  return (
    <div className="relative">
      <TextField
        label={label}
        error={error}
        hint={hint}
        action={action}
        type={revealed ? 'text' : 'password'}
        className={cn('pr-9', className)}
        {...props}
      />
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        tabIndex={-1}
        // Offset from the top of the group rather than centred, so the control
        // stays on the input when a message appears beneath it.
        className="absolute top-6.5 right-1 text-muted-foreground"
        aria-label={revealed ? 'Hide password' : 'Show password'}
        onClick={() => setRevealed((current) => !current)}
      >
        <Icon className="size-4" aria-hidden="true" />
      </Button>
    </div>
  )
}
