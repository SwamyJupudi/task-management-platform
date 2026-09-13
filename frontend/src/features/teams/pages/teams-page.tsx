import { LockIcon, PlusIcon, UsersRoundIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'

import { TeamFormDialog } from '../components/team-form-dialog'
import { useTeamPermissions, useTeams } from '../hooks'
import type { Team, TeamStatus } from '../types'

/**
 * The teams of a workspace.
 *
 * The status filter and the page live in the query string, so a narrowed
 * listing is a link somebody can send. Status is the only filter the endpoint
 * takes — there is no search — so it is the only one offered.
 *
 * Cards rather than a table: a team is four facts and a way in, and the roster
 * count and the lead read better beside the name than down a column.
 */

const ANY = '__any__'

function isStatus(value: string | null): value is TeamStatus {
  return value === 'ACTIVE' || value === 'ARCHIVED'
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const second = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return `${first}${second}`.toUpperCase() || '?'
}

function TeamCard({ team, workspaceSlug }: { team: Team; workspaceSlug: string }) {
  return (
    <li className="rounded-lg border border-border p-4">
      <div className="flex items-start justify-between gap-2">
        <Link
          to={paths.workspace.team(workspaceSlug, team.id)}
          className="min-w-0 truncate font-medium hover:underline"
        >
          {team.name}
        </Link>
        {team.status === 'ARCHIVED' ? (
          <Badge variant="outline" className="border-dashed">
            Archived
          </Badge>
        ) : null}
      </div>

      {team.description ? (
        <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{team.description}</p>
      ) : null}

      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2">
        {team.leadName ? (
          <span className="flex items-center gap-1.5">
            <Avatar size="sm">
              <AvatarFallback>{initials(team.leadName)}</AvatarFallback>
            </Avatar>
            <span className="text-xs text-muted-foreground">{team.leadName} leads</span>
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">No lead</span>
        )}
        <span className="text-xs text-muted-foreground tabular-nums">
          {team.memberCount.toLocaleString()} member{team.memberCount === 1 ? '' : 's'}
        </span>
      </div>
    </li>
  )
}

export function TeamsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()
  const navigate = useNavigate()
  const permissions = useTeamPermissions()
  const [creating, setCreating] = useState(false)

  const statusParam = searchParams.get('status')
  const status = isStatus(statusParam) ? statusParam : undefined
  const pageIndex = Math.max(Number(searchParams.get('page') ?? '1') - 1, 0)

  const teams = useTeams(status, pageIndex)

  const setParams = (mutate: (params: URLSearchParams) => void, resetPage = true) => {
    const next = new URLSearchParams(searchParams)
    mutate(next)
    if (resetPage) next.delete('page')
    setSearchParams(next, { replace: true })
  }

  const header = (
    <PageHeader
      title="Teams"
      description={workspace ? `In ${workspace.workspaceName}` : undefined}
      actions={
        permissions.canCreate ? (
          <Button onClick={() => setCreating(true)}>
            <PlusIcon aria-hidden="true" />
            New team
          </Button>
        ) : undefined
      }
    />
  )

  if (!permissions.canRead) {
    return (
      <div className="space-y-6">
        {header}
        <EmptyState
          icon={LockIcon}
          title="You cannot see teams here"
          description="Your role in this workspace does not include reading teams. An administrator can grant it."
        />
      </div>
    )
  }

  const page = teams.data
  const slug = workspace?.workspaceSlug ?? ''

  return (
    <div className="space-y-6">
      {header}

      <Select
        value={status ?? ANY}
        onValueChange={(value) =>
          setParams((params) => {
            if (value === ANY) params.delete('status')
            else params.set('status', value)
          })
        }
      >
        <SelectTrigger className="h-8 w-[10rem]" aria-label="Filter by status">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ANY}>Any status</SelectItem>
          <SelectItem value="ACTIVE">Active</SelectItem>
          <SelectItem value="ARCHIVED">Archived</SelectItem>
        </SelectContent>
      </Select>

      {teams.isError ? (
        <ErrorState error={teams.error} onRetry={() => void teams.refetch()} />
      ) : teams.isPending ? (
        <LoadingState label="Loading teams" />
      ) : page && page.content.length === 0 ? (
        <EmptyState
          icon={UsersRoundIcon}
          title={status ? 'No teams match that filter' : 'No teams yet'}
          description={
            status
              ? 'Clear the filter to see every team in this workspace.'
              : 'Teams group people and the projects they run.'
          }
          action={
            !status && permissions.canCreate ? (
              <Button onClick={() => setCreating(true)}>
                <PlusIcon aria-hidden="true" />
                New team
              </Button>
            ) : undefined
          }
        />
      ) : page ? (
        <div className="space-y-4">
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {page.content.map((team) => (
              <TeamCard key={team.id} team={team} workspaceSlug={slug} />
            ))}
          </ul>
          <PaginationBar
            page={page}
            onPageChange={(next) =>
              setParams((params) => params.set('page', String(next + 1)), false)
            }
          />
        </div>
      ) : null}

      <TeamFormDialog
        open={creating}
        onOpenChange={setCreating}
        onCreated={(created) => void navigate(paths.workspace.team(slug, created.id))}
      />
    </div>
  )
}
