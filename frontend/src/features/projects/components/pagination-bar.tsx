import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import type { Page } from '@/lib/api'

/**
 * Page controls, driven by the envelope the backend already returns.
 *
 * `first` and `last` are read rather than derived from the page index and the
 * total, because the server computes them and a client that recalculated them
 * would be a second opinion on the same question.
 *
 * The page number is displayed one-based and sent zero-based, which is the one
 * translation this component exists to hide.
 */
export function PaginationBar<T>({
  page,
  onPageChange,
}: {
  page: Page<T>
  onPageChange: (nextZeroBased: number) => void
}) {
  if (page.totalElements === 0) return null

  const from = page.page * page.size + 1
  const to = page.page * page.size + page.content.length

  return (
    <nav
      aria-label="Pagination"
      className="flex flex-col items-center justify-between gap-2 sm:flex-row"
    >
      <p className="text-xs text-muted-foreground tabular-nums" aria-live="polite">
        {from}–{to} of {page.totalElements.toLocaleString()}
      </p>

      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={page.first}
          onClick={() => onPageChange(page.page - 1)}
        >
          <ChevronLeftIcon aria-hidden="true" />
          Previous
        </Button>

        <span className="text-xs text-muted-foreground tabular-nums">
          Page {page.page + 1} of {Math.max(page.totalPages, 1)}
        </span>

        <Button
          variant="outline"
          size="sm"
          disabled={page.last}
          onClick={() => onPageChange(page.page + 1)}
        >
          Next
          <ChevronRightIcon aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
