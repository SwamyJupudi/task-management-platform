import { ScrollTextIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'

import { AdminShell } from '../components/admin-shell'
import { PlatformActivityList } from '../components/platform-activity-list'
import { useAdminPermissions, usePlatformActivity } from '../hooks'

/**
 * The platform audit trail.
 *
 * What happened outside any workspace: account administration, and grants of
 * the platform role. A workspace's own history is a different endpoint behind a
 * different gate, and the two are disjoint by construction — a row belongs to
 * exactly one of them, decided by whether it names a workspace — so neither can
 * become a way into the other.
 *
 * No filters and no sort, because the endpoint offers neither. Newest first is
 * the only order a history is read in, and a control that reordered it in the
 * browser would reorder one page rather than the trail.
 *
 * Two codes, not either: `admin:read_system` says the caller may look across
 * the installation and `activity:read` says they may read an audit trail at
 * all. Somebody holding one without the other is told which is missing rather
 * than shown an empty page.
 */
export function PlatformActivityPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canReadPlatformActivity } = useAdminPermissions()

  const rawPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) - 1 : 0

  const trail = usePlatformActivity(page)

  if (!canReadPlatformActivity) {
    return (
      <AdminShell title="Platform activity">
        <EmptyState
          icon={ScrollTextIcon}
          title="You cannot read the platform trail"
          description="It needs both admin:read_system and activity:read on the platform. Reading a workspace's own history is a separate grant inside that workspace."
        />
      </AdminShell>
    )
  }

  const data = trail.data

  return (
    <AdminShell
      title="Platform activity"
      description="Account administration and platform-role grants, newest first"
    >
      {trail.isError ? (
        <ErrorState error={trail.error} onRetry={() => void trail.refetch()} />
      ) : trail.isPending ? (
        <LoadingState label="Loading the audit trail" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={ScrollTextIcon}
          title="Nothing recorded yet"
          description="Administering an account or granting the platform role writes a row here."
        />
      ) : data ? (
        <div className="space-y-4">
          <PlatformActivityList entries={data.content} />
          <PaginationBar
            page={data}
            onPageChange={(next) => {
              const params = new URLSearchParams(searchParams)
              params.set('page', String(next + 1))
              setSearchParams(params, { replace: true })
            }}
          />
        </div>
      ) : null}
    </AdminShell>
  )
}
