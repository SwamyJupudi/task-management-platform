import { CrownIcon, PlusIcon, UserMinusIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useWorkspaceMembers } from '@/features/people'
import { toUserMessage } from '@/lib/api'

import { useTeamMembers, useTeamMutations } from '../hooks'
import type { Team } from '../types'

/**
 * The roster of one team, and who leads it.
 *
 * Leadership is a property of the team rather than a flag on a row, which is
 * why naming a lead is a separate call that also puts that person in the team.
 * The backend refuses to remove somebody who leads it, and that refusal is
 * surfaced as it comes rather than pre-empted here — the rule belongs to the
 * server.
 *
 * The picker lists workspace members who are not already on the team, because
 * the endpoint takes somebody who is already in the workspace. Without
 * `member:read` there is no picker, and the roster is still readable.
 */

function initials(first: string, last: string): string {
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase() || '?'
}

export function TeamMembersPanel({ team, canManage }: { team: Team; canManage: boolean }) {
  const members = useTeamMembers(team.id)
  const options = useWorkspaceMembers()
  const { addMember, removeMember, setLead } = useTeamMutations(team.id)

  const [selected, setSelected] = useState('')

  const rows = members.data?.content ?? []
  const onTeam = new Set(rows.map((row) => row.userId))
  const addable = (options.data ?? []).filter((option) => !onTeam.has(option.userId))

  const busy = addMember.isPending || removeMember.isPending || setLead.isPending

  if (members.isError) {
    return <ErrorState error={members.error} onRetry={() => void members.refetch()} />
  }

  return (
    <div className="space-y-4">
      {canManage && options.data ? (
        <div className="flex flex-col gap-2 sm:flex-row">
          <Select value={selected} onValueChange={setSelected} disabled={addable.length === 0}>
            <SelectTrigger className="h-9 flex-1" aria-label="Choose somebody to add">
              <SelectValue
                placeholder={addable.length === 0 ? 'Everybody is already on it' : 'Add a member'}
              />
            </SelectTrigger>
            <SelectContent>
              {addable.map((option) => (
                <SelectItem key={option.userId} value={option.userId}>
                  {option.firstName} {option.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button
            disabled={selected === '' || busy}
            onClick={async () => {
              try {
                await addMember.mutateAsync(selected)
                setSelected('')
                toast.success('Member added.')
              } catch (error) {
                toast.error(toUserMessage(error))
              }
            }}
          >
            <PlusIcon aria-hidden="true" />
            Add
          </Button>
        </div>
      ) : null}

      {members.isPending ? (
        <div className="space-y-2" role="status" aria-live="polite">
          <span className="sr-only">Loading the roster</span>
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : rows.length === 0 ? (
        <EmptyState
          title="Nobody on this team yet"
          description="Add workspace members so the team has people to run its projects."
          className="border-0 px-0 py-6"
        />
      ) : (
        <ul className="divide-y divide-border">
          {rows.map((member) => (
            <li key={member.userId} className="flex items-center gap-3 py-2">
              <Avatar size="sm">
                <AvatarFallback>{initials(member.firstName, member.lastName)}</AvatarFallback>
              </Avatar>

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">
                  {member.firstName} {member.lastName}
                </p>
                <p className="truncate text-xs text-muted-foreground">{member.email}</p>
              </div>

              {member.lead ? (
                <Badge variant="secondary" className="shrink-0">
                  <CrownIcon aria-hidden="true" />
                  Lead
                </Badge>
              ) : null}

              {canManage ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      disabled={busy}
                      aria-label={`Manage ${member.firstName} ${member.lastName}`}
                    >
                      <span aria-hidden="true">⋯</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    {member.lead ? (
                      <DropdownMenuItem
                        onSelect={async () => {
                          try {
                            await setLead.mutateAsync(null)
                            toast.success('Lead cleared.')
                          } catch (error) {
                            toast.error(toUserMessage(error))
                          }
                        }}
                      >
                        Remove as lead
                      </DropdownMenuItem>
                    ) : (
                      <DropdownMenuItem
                        onSelect={async () => {
                          try {
                            await setLead.mutateAsync(member.userId)
                            toast.success('Lead updated.')
                          } catch (error) {
                            toast.error(toUserMessage(error))
                          }
                        }}
                      >
                        <CrownIcon aria-hidden="true" />
                        Make lead
                      </DropdownMenuItem>
                    )}

                    <DropdownMenuSeparator />

                    <DropdownMenuItem
                      variant="destructive"
                      onSelect={async () => {
                        try {
                          await removeMember.mutateAsync(member.userId)
                          toast.success('Member removed.')
                        } catch (error) {
                          // Includes the backend refusing to remove the lead,
                          // which is its rule to state rather than ours.
                          toast.error(toUserMessage(error))
                        }
                      }}
                    >
                      <UserMinusIcon aria-hidden="true" />
                      Remove from team
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
