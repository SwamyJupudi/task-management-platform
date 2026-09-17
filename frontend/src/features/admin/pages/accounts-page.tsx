import { UsersIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { AccountFilters } from '../components/account-filters'
import { AccountTable } from '../components/account-table'
import { AdminShell } from '../components/admin-shell'
import { ACCOUNT_SORTS, DEFAULT_ACCOUNT_SORT, isAccountStatus } from '../constants'
import { useAccounts, useAdminPermissions } from '../hooks'
import type { AccountFilters as Filters } from '../types'

/**
 * Account management: who exists, who cannot sign in, and what to do about it.
 *
 * Filters and paging live in the query string, so a narrowed directory —
 * "everybody locked out", "everybody still awaiting verification" — is a link
 * somebody can send to a colleague.
 *
 * Every verb behind the row menu is a separate endpoint with its own permission
 * code, and each is offered only when the signed-in account holds that code on
 * the platform. There is deliberately no control that sets somebody's password:
 * the platform has no endpoint for it, an administrator starts a recovery and
 * the token goes to the address that owns the account.
 *
 * Account deletion is likewise absent. `DELETE /users/{id}` exists and this
 * phase does not use it, so nothing here can remove an account.
 */
export function AccountsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canReadAccounts } = useAdminPermissions()

  const statusParam = searchParams.get('status')
  const filters: Filters = {
    search: searchParams.get('search') ?? undefined,
    status: statusParam !== null && isAccountStatus(statusParam) ? statusParam : undefined,
    locked: searchParams.get('locked') === 'true' ? true : undefined,
  }

  const rawPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) - 1 : 0

  const sortParam = searchParams.get('sort')
  const sort =
    sortParam !== null && ACCOUNT_SORTS.some((option) => option.value === sortParam)
      ? sortParam
      : DEFAULT_ACCOUNT_SORT

  const directory = useAccounts(filters, page, sort)
  const apply = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams)
    next.set(key, value)
    if (key !== 'page') next.delete('page')
    apply(next)
  }

  if (!canReadAccounts) {
    return (
      <AdminShell title="Accounts">
        <EmptyState
          icon={UsersIcon}
          title="You cannot see the account directory"
          description="Reading accounts across the installation needs user:read on the platform. A workspace grant does not carry here."
        />
      </AdminShell>
    )
  }

  const data = directory.data
  const filtered =
    filters.search !== undefined || filters.status !== undefined || filters.locked === true

  return (
    <AdminShell
      title="Accounts"
      description="Every account in this installation, and what it can do"
    >
      <AccountFilters params={searchParams} onChange={apply}>
        <div className="flex items-center gap-2">
          <Label htmlFor="account-sort" className="shrink-0 text-xs text-muted-foreground">
            Sort
          </Label>
          <Select value={sort} onValueChange={(value) => setParam('sort', value)}>
            <SelectTrigger id="account-sort" className="h-8 w-[12rem]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ACCOUNT_SORTS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </AccountFilters>

      {directory.isError ? (
        <ErrorState error={directory.error} onRetry={() => void directory.refetch()} />
      ) : directory.isPending ? (
        <LoadingState label="Loading accounts" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={UsersIcon}
          title={filtered ? 'No accounts match' : 'No accounts yet'}
          description={
            filtered
              ? 'Nothing in the installation matches these filters.'
              : 'Accounts appear here as people register.'
          }
        />
      ) : data ? (
        <div className="space-y-4">
          <AccountTable accounts={data.content} />
          <PaginationBar page={data} onPageChange={(next) => setParam('page', String(next + 1))} />
        </div>
      ) : null}
    </AdminShell>
  )
}
