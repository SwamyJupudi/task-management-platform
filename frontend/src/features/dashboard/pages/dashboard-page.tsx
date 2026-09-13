import { ActivityIcon } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { PageHeader } from '@/components/common/page-header'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { useSessionStore } from '@/stores/session-store'

/**
 * The workspace's landing screen.
 *
 * A placeholder, and honestly one: the figures are em-dashes rather than
 * invented numbers, because a dashboard that shows plausible fiction is worse
 * than one that shows nothing. Every figure here comes from
 * `GET /workspaces/{id}/dashboard`, which the dashboard phase wires up; what
 * this screen establishes now is the layout those figures land in, and that
 * the shell around it resolves a workspace, a role and a permission set.
 */

const stats: readonly string[] = ['My open tasks', 'Due this week', 'Overdue', 'Active projects']

export function DashboardPage() {
  const workspace = useActiveWorkspace()
  const user = useSessionStore((state) => state.user)

  return (
    <div className="space-y-6">
      <PageHeader
        title={user ? `Welcome back, ${user.firstName}` : 'Dashboard'}
        description={
          workspace ? `${workspace.workspaceName} · you are ${workspace.roleName} here` : undefined
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((label) => (
          <Card key={label}>
            <CardHeader>
              <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-semibold tabular-nums" aria-hidden="true">
                —
              </p>
              <p className="text-xs text-muted-foreground">Not available yet</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <EmptyState
        icon={ActivityIcon}
        title="Nothing to show here yet"
        description="Your tasks, deadlines and recent activity will appear on this screen once the dashboard is built."
      />
    </div>
  )
}
