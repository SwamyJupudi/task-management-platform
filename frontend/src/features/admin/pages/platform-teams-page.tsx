import { UsersRoundIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { AdminShell } from '../components/admin-shell'
import { TeamTable } from '../components/team-table'
import { WorkspacePicker } from '../components/workspace-picker'
import { TEAM_STATUSES, isTeamStatus } from '../constants'
import { useAdminPermissions, useWorkspaceOptions, useWorkspaceTeams } from '../hooks'

/**
 * Teams, one workspace at a time.
 *
 * **A limit of the API rather than a choice.** There is no cross-workspace team
 * listing: `GET /admin/statistics` counts teams across the installation and
 * `GET /workspaces/{id}/teams` lists them for one workspace. Concatenating
 * every workspace's teams here would be one request per workspace and a table
 * that could not be paged honestly — the page numbers would be each workspace's
 * rather than the installation's — so the screen asks which workspace instead.
 *
 * The workspace is the address of the records, not a scope. `team:read` held on
 * the platform satisfies the check in every workspace, because the backend
 * resolves a workspace permission against the union of the caller's platform
 * role and their membership. That is what lets an administrator read the teams
 * of a workspace they have never joined.
 */

/** A Select item cannot hold an empty value. */
const ANY = '__any__'

export function PlatformTeamsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const permissions = useAdminPermissions()

  const workspaces = useWorkspaceOptions()

  const workspaceId = searchParams.get('workspaceId') ?? undefined
  const statusParam = searchParams.get('status')
  const status = statusParam !== null && isTeamStatus(statusParam) ? statusParam : undefined

  const rawPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) - 1 : 0

  const teams = useWorkspaceTeams(workspaceId, status, page)
  const workspace = workspaces.data?.find((candidate) => candidate.id === workspaceId)

  const setParam = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(searchParams)
    if (value === undefined) next.delete(key)
    else next.set(key, value)
    if (key !== 'page') next.delete('page')
    setSearchParams(next, { replace: true })
  }

  if (!permissions.canReadTeams || !permissions.canListWorkspaces) {
    return (
      <AdminShell title="Teams">
        <EmptyState
          icon={UsersRoundIcon}
          title="You cannot browse teams here"
          description="This screen needs workspace:read and team:read on the platform. A workspace grant covers that workspace's own team screens, not this panel."
        />
      </AdminShell>
    )
  }

  const data = teams.data

  return (
    <AdminShell
      title="Teams"
      description="The teams of one workspace. No endpoint lists them across the installation."
      actions={
        workspaces.data ? (
          <WorkspacePicker
            id="teams-workspace"
            workspaces={workspaces.data}
            value={workspaceId}
            onChange={(next) => setParam('workspaceId', next)}
          />
        ) : undefined
      }
    >
      {workspaceId !== undefined ? (
        <Select
          value={status ?? ANY}
          onValueChange={(value) => setParam('status', value === ANY ? undefined : value)}
        >
          <SelectTrigger className="h-8 w-[11rem]" aria-label="Filter by team status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY}>Any status</SelectItem>
            {TEAM_STATUSES.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      ) : null}

      {workspaces.isError ? (
        <ErrorState error={workspaces.error} onRetry={() => void workspaces.refetch()} />
      ) : workspaces.isPending ? (
        <LoadingState label="Loading workspaces" />
      ) : workspaceId === undefined ? (
        <EmptyState
          icon={UsersRoundIcon}
          title="Choose a workspace"
          description="Teams belong to a workspace, and the API lists them one workspace at a time."
        />
      ) : teams.isError ? (
        <ErrorState error={teams.error} onRetry={() => void teams.refetch()} />
      ) : teams.isPending ? (
        <LoadingState label="Loading teams" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={UsersRoundIcon}
          title={
            status !== undefined
              ? 'No teams match'
              : `${workspace?.name ?? 'This workspace'} has no teams`
          }
          description={
            status !== undefined
              ? 'No team in this workspace is in that state.'
              : 'Teams created inside the workspace appear here.'
          }
        />
      ) : data ? (
        <div className="space-y-4">
          <TeamTable teams={data.content} />
          <PaginationBar page={data} onPageChange={(next) => setParam('page', String(next + 1))} />
        </div>
      ) : null}
    </AdminShell>
  )
}
