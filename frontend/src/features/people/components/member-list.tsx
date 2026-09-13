import { UserMinusIcon } from 'lucide-react'
import { toast } from 'sonner'

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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { toUserMessage } from '@/lib/api'
import { useSessionStore } from '@/stores/session-store'

import { usePeopleMutations, usePeoplePermissions, useWorkspaceRoles } from '../hooks'
import type { WorkspaceMember } from '../types'

/**
 * The workspace roster.
 *
 * A table from `md` and cards below it, because these rows are compared down a
 * column — who holds which role — rather than opened one at a time.
 *
 * The role is a dropdown when the caller may change it and a badge when they
 * may not, so the same column reads the same way either way. Changing a role
 * needs `member:assign_role` and listing the roles to choose from needs
 * `role:read`; without the second there is nothing to populate a picker with,
 * so the badge is shown instead of an empty dropdown.
 *
 * An account's own row offers no controls. Removing yourself from a workspace
 * or demoting yourself out of the permission you are using is the kind of thing
 * the backend may well allow and nobody means to do.
 */

function initials(first: string, last: string): string {
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase() || '?'
}

function formatDate(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function RoleCell({ member, isSelf }: { member: WorkspaceMember; isSelf: boolean }) {
  const permissions = usePeoplePermissions()
  const roles = useWorkspaceRoles()
  const { changeRole } = usePeopleMutations()

  const editable = permissions.canAssignRole && !isSelf && roles.data && roles.data.length > 0

  if (!editable) {
    return <Badge variant="outline">{member.roleName}</Badge>
  }

  return (
    <Select
      value={member.roleSlug}
      disabled={changeRole.isPending}
      onValueChange={async (roleSlug) => {
        try {
          await changeRole.mutateAsync({ userId: member.userId, roleSlug })
          toast.success('Role updated.')
        } catch (error) {
          toast.error(toUserMessage(error))
        }
      }}
    >
      <SelectTrigger
        className="h-8 w-[10rem]"
        aria-label={`Role for ${member.firstName} ${member.lastName}`}
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {(roles.data ?? []).map((role) => (
          <SelectItem key={role.slug} value={role.slug}>
            {role.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function RemoveButton({ member, isSelf }: { member: WorkspaceMember; isSelf: boolean }) {
  const permissions = usePeoplePermissions()
  const { removeMember } = usePeopleMutations()

  if (!permissions.canRemove || isSelf) return null

  return (
    <Button
      variant="ghost"
      size="icon-sm"
      disabled={removeMember.isPending}
      aria-label={`Remove ${member.firstName} ${member.lastName} from the workspace`}
      onClick={async () => {
        try {
          await removeMember.mutateAsync(member.userId)
          toast.success('Member removed.')
        } catch (error) {
          toast.error(toUserMessage(error))
        }
      }}
    >
      <UserMinusIcon aria-hidden="true" />
    </Button>
  )
}

export function MemberList({ members }: { members: WorkspaceMember[] }) {
  const selfId = useSessionStore((state) => state.user?.id ?? null)

  return (
    <>
      {/* Cards below md. */}
      <ul className="space-y-3 md:hidden">
        {members.map((member) => {
          const isSelf = member.userId === selfId
          return (
            <li key={member.userId} className="rounded-lg border border-border p-4">
              <div className="flex items-start gap-3">
                <Avatar size="sm" className="mt-0.5">
                  <AvatarFallback>{initials(member.firstName, member.lastName)}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">
                    {member.firstName} {member.lastName}
                    {isSelf ? <span className="text-muted-foreground"> (you)</span> : null}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">{member.email}</p>
                </div>
                <RemoveButton member={member} isSelf={isSelf} />
              </div>

              <div className="mt-3 flex flex-wrap items-center gap-2">
                <RoleCell member={member} isSelf={isSelf} />
                {member.userStatus !== 'ACTIVE' ? (
                  <Badge variant="secondary">{member.userStatus.toLowerCase()}</Badge>
                ) : null}
                <span className="text-xs text-muted-foreground">
                  joined {formatDate(member.joinedAt)}
                </span>
              </div>
            </li>
          )
        })}
      </ul>

      {/* Table from md. */}
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Person</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Joined</TableHead>
              <TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {members.map((member) => {
              const isSelf = member.userId === selfId
              return (
                <TableRow key={member.userId}>
                  <TableCell className="max-w-[20rem]">
                    <span className="flex items-center gap-2">
                      <Avatar size="sm">
                        <AvatarFallback>
                          {initials(member.firstName, member.lastName)}
                        </AvatarFallback>
                      </Avatar>
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium">
                          {member.firstName} {member.lastName}
                          {isSelf ? <span className="text-muted-foreground"> (you)</span> : null}
                        </span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {member.email}
                        </span>
                      </span>
                    </span>
                  </TableCell>
                  <TableCell>
                    <RoleCell member={member} isSelf={isSelf} />
                  </TableCell>
                  <TableCell>
                    {member.userStatus === 'ACTIVE' ? (
                      <span className="text-sm text-muted-foreground">Active</span>
                    ) : (
                      <Badge variant="secondary">{member.userStatus.toLowerCase()}</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right whitespace-nowrap text-muted-foreground">
                    {formatDate(member.joinedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <RemoveButton member={member} isSelf={isSelf} />
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>
    </>
  )
}
