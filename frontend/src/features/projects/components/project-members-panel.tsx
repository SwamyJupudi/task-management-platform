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
import { toUserMessage } from '@/lib/api'

import {
  useAddProjectMember,
  useAssignProjectOwner,
  useClearProjectOwner,
  useMemberOptions,
  useProjectMembers,
  useRemoveProjectMember,
} from '../hooks'
import type { Project } from '../types'

/**
 * The project roster, and who owns it.
 *
 * Ownership is a property of the project rather than a flag on a row, which is
 * why naming an owner is a separate call that also puts that person on the
 * project. The backend refuses to remove somebody who owns it, and that refusal
 * is surfaced as it comes rather than pre-empted here, because the rule belongs
 * to the server.
 *
 * The picker lists workspace members who are not already on the project, since
 * the endpoint takes somebody who is already in the workspace. Without
 * `member:read` there is no picker, and the roster is still readable.
 */

function initials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase() || '?'
}

export function ProjectMembersPanel({
  project,
  canManage,
}: {
  project: Project
  canManage: boolean
}) {
  const members = useProjectMembers(project.id)
  const options = useMemberOptions()

  const addMember = useAddProjectMember(project.id)
  const removeMember = useRemoveProjectMember(project.id)
  const assignOwner = useAssignProjectOwner(project.id)
  const clearOwner = useClearProjectOwner(project.id)

  const [selected, setSelected] = useState('')

  const rows = members.data?.content ?? []
  const onProject = new Set(rows.map((row) => row.userId))
  const addable = (options.data ?? []).filter((option) => !onProject.has(option.userId))

  const busy =
    addMember.isPending || removeMember.isPending || assignOwner.isPending || clearOwner.isPending

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
          title="Nobody on this project yet"
          description="Add workspace members so they can be assigned work here."
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

              {member.owner ? (
                <Badge variant="secondary" className="shrink-0">
                  <CrownIcon aria-hidden="true" />
                  Owner
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
                    {member.owner ? (
                      <DropdownMenuItem
                        onSelect={async () => {
                          try {
                            await clearOwner.mutateAsync()
                            toast.success('Owner cleared.')
                          } catch (error) {
                            toast.error(toUserMessage(error))
                          }
                        }}
                      >
                        Remove as owner
                      </DropdownMenuItem>
                    ) : (
                      <DropdownMenuItem
                        onSelect={async () => {
                          try {
                            await assignOwner.mutateAsync(member.userId)
                            toast.success('Owner updated.')
                          } catch (error) {
                            toast.error(toUserMessage(error))
                          }
                        }}
                      >
                        <CrownIcon aria-hidden="true" />
                        Make owner
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
                          // Includes the backend refusing to remove the owner,
                          // which is its rule to state rather than ours.
                          toast.error(toUserMessage(error))
                        }
                      }}
                    >
                      <UserMinusIcon aria-hidden="true" />
                      Remove from project
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
