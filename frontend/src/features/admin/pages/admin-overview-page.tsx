import {
  BuildingIcon,
  CircleAlertIcon,
  HardDriveIcon,
  ListChecksIcon,
  LockIcon,
  UsersIcon,
} from 'lucide-react'
import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

import { DistributionChart } from '@/components/charts/distribution-chart'
import { ErrorState } from '@/components/common/error-state'
import { Panel } from '@/components/common/panel'
import { StatCard } from '@/components/common/stat-card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { CountByKey } from '@/types/reports'

import { AdminShell } from '../components/admin-shell'
import {
  DEFAULT_WINDOW_DAYS,
  WINDOW_OPTIONS,
  formatBytes,
  humanise,
  isWindowOption,
} from '../constants'
import { useAdminPermissions, useSystemStatistics } from '../hooks'
import type { CountsByKey } from '../types'

/**
 * The installation at a glance.
 *
 * One request behind the whole screen, because the backend assembles it in a
 * single read-only transaction: the panels agree with each other, and a task
 * finished halfway through cannot be counted as open in one figure and done in
 * the next. Splitting it into a request per panel would throw that away.
 *
 * Only the five "recent" figures depend on the window. Everything else is a
 * live count, and the panels say which is which — a headline that silently
 * meant "in the last thirty days" would be read as a total.
 *
 * There is nothing operational here: no uptime, no memory, no request rate.
 * Those are Actuator's and the log platform's, they are not database questions,
 * and a second worse copy of them on this screen would be read as authoritative.
 */

/** The chart takes labelled rows; the statistics arrive as a map of raw keys. */
function toRows(counts: CountsByKey): CountByKey[] {
  return Object.entries(counts)
    .map(([key, count]) => ({ key, label: humanise(key), count }))
    .sort((a, b) => b.count - a.count)
}

export function AdminOverviewPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canReadSystem } = useAdminPermissions()

  const requested = Number(searchParams.get('windowDays') ?? DEFAULT_WINDOW_DAYS)
  // Anything not on the list falls back rather than being sent. The backend
  // refuses a window under a day or over its cap, and a mistyped link is not
  // worth a 400.
  const windowDays = isWindowOption(requested) ? requested : DEFAULT_WINDOW_DAYS

  const statistics = useSystemStatistics(windowDays)
  const data = statistics.data

  const accountRows = useMemo(() => (data ? toRows(data.accounts.byStatus) : []), [data])
  const projectRows = useMemo(() => (data ? toRows(data.work.projectsByStatus) : []), [data])
  const taskRows = useMemo(() => (data ? toRows(data.work.tasksByStatus) : []), [data])
  const workspaceRows = useMemo(() => (data ? toRows(data.workspaces.byStatus) : []), [data])

  if (!canReadSystem) {
    return (
      <AdminShell title="Platform overview">
        <ErrorState error={new Error('You do not hold admin:read_system on the platform.')} />
      </AdminShell>
    )
  }

  return (
    <AdminShell
      title="Platform overview"
      description={
        data
          ? `Read ${new Date(data.generatedAt).toLocaleString()}. Nothing here is cached.`
          : 'Counts across every workspace in this installation'
      }
      actions={
        <div className="flex items-center gap-2">
          <Label htmlFor="stats-window" className="shrink-0 text-xs text-muted-foreground">
            Recent activity
          </Label>
          <Select
            value={String(windowDays)}
            onValueChange={(value) => {
              const next = new URLSearchParams(searchParams)
              if (Number(value) === DEFAULT_WINDOW_DAYS) next.delete('windowDays')
              else next.set('windowDays', value)
              setSearchParams(next, { replace: true })
            }}
          >
            <SelectTrigger id="stats-window" className="h-8 w-[10rem]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {WINDOW_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={String(option.value)}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      }
    >
      {statistics.isError ? (
        <ErrorState error={statistics.error} onRetry={() => void statistics.refetch()} />
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Accounts"
          value={data?.accounts.total ?? 0}
          icon={UsersIcon}
          loading={statistics.isPending}
          hint="Live accounts across the installation"
        />
        <StatCard
          label="Locked out"
          value={data?.accounts.locked ?? 0}
          icon={LockIcon}
          tone="destructive"
          loading={statistics.isPending}
          hint="Right now, and not the same as deactivated"
        />
        <StatCard
          label="Workspaces"
          value={data?.workspaces.total ?? 0}
          icon={BuildingIcon}
          loading={statistics.isPending}
          hint={
            data
              ? `${data.workspaces.teams.toLocaleString()} teams · ${data.workspaces.memberships.toLocaleString()} memberships`
              : undefined
          }
        />
        <StatCard
          label="Overdue tasks"
          value={data?.work.tasksOverdue ?? 0}
          icon={CircleAlertIcon}
          tone="destructive"
          loading={statistics.isPending}
          hint="Measured in UTC: this figure spans timezones"
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Projects"
          value={data?.work.projects ?? 0}
          loading={statistics.isPending}
        />
        <StatCard
          label="Tasks"
          value={data?.work.tasks ?? 0}
          icon={ListChecksIcon}
          loading={statistics.isPending}
        />
        <StatCard
          label="Attachments"
          value={data?.storage.attachments ?? 0}
          icon={HardDriveIcon}
          loading={statistics.isPending}
        />
        <StatCard
          label="Stored"
          value={data ? formatBytes(data.storage.totalBytes) : '—'}
          icon={HardDriveIcon}
          loading={statistics.isPending}
          hint="Live files only, so the store is at least this large"
        />
      </div>

      <Panel
        title={`The last ${data?.windowDays ?? windowDays} days`}
        description="The only figures on this screen bounded by a window"
        isLoading={statistics.isPending}
        error={statistics.error}
        onRetry={() => void statistics.refetch()}
      >
        <dl className="grid gap-4 sm:grid-cols-3">
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">Accounts created</dt>
            <dd className="text-2xl font-semibold tabular-nums">
              {(data?.recent.accountsCreated ?? 0).toLocaleString()}
            </dd>
          </div>
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">Accounts that signed in</dt>
            <dd className="text-2xl font-semibold tabular-nums">
              {(data?.recent.accountsSignedIn ?? 0).toLocaleString()}
            </dd>
          </div>
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">Audit rows written</dt>
            <dd className="text-2xl font-semibold tabular-nums">
              {(data?.recent.auditRowsWritten ?? 0).toLocaleString()}
            </dd>
          </div>
        </dl>
      </Panel>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          title="Accounts by status"
          isLoading={statistics.isPending}
          error={statistics.error}
          onRetry={() => void statistics.refetch()}
          isEmpty={accountRows.length === 0}
          emptyTitle="No accounts yet"
        >
          <DistributionChart data={accountRows} ariaLabel="Accounts by status" />
        </Panel>

        <Panel
          title="Workspaces by status"
          isLoading={statistics.isPending}
          error={statistics.error}
          onRetry={() => void statistics.refetch()}
          isEmpty={workspaceRows.length === 0}
          emptyTitle="No workspaces yet"
        >
          <DistributionChart data={workspaceRows} ariaLabel="Workspaces by status" />
        </Panel>

        <Panel
          title="Projects by status"
          isLoading={statistics.isPending}
          error={statistics.error}
          onRetry={() => void statistics.refetch()}
          isEmpty={projectRows.length === 0}
          emptyTitle="No projects yet"
        >
          <DistributionChart data={projectRows} ariaLabel="Projects by status" />
        </Panel>

        <Panel
          title="Tasks by status"
          isLoading={statistics.isPending}
          error={statistics.error}
          onRetry={() => void statistics.refetch()}
          isEmpty={taskRows.length === 0}
          emptyTitle="No tasks yet"
        >
          <DistributionChart data={taskRows} ariaLabel="Tasks by status" />
        </Panel>
      </div>
    </AdminShell>
  )
}
