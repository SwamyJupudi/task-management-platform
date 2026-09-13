import { LockIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { PageHeader } from '@/components/common/page-header'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { useSessionStore } from '@/stores/session-store'

import { EmployeeDashboard } from '../components/employee-dashboard'
import { WorkspaceDashboard } from '../components/workspace-dashboard'
import { useCanSeeMyDashboard, useCanSeeWorkspaceDashboard } from '../hooks'

/**
 * The workspace's landing screen: two dashboards behind one route.
 *
 * Two, because the requirements describe two and the backend serves them from
 * two endpoints with two different gates. One route rather than two, because
 * they are the same question asked at a different width, and a person who holds
 * both wants to move between them without leaving the screen.
 *
 * The chosen view lives in the query string rather than in component state, so
 * a link to "the workspace view" is a link somebody can send. An unknown or
 * unpermitted value falls back to the view the caller may actually see rather
 * than erroring, because a stale bookmark is not a failure.
 *
 * Both tabs repeat the permission their endpoint is gated on. That is a
 * courtesy — it avoids a request the interface already knows will be refused —
 * and not a control: the backend checks again, and is the only thing that
 * decides.
 */

type View = 'me' | 'workspace'

export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()
  const user = useSessionStore((state) => state.user)

  const canSeeWorkspace = useCanSeeWorkspaceDashboard()
  const canSeeMine = useCanSeeMyDashboard()

  const requested = searchParams.get('view')
  const view: View = requested === 'workspace' && canSeeWorkspace ? 'workspace' : 'me'

  const onViewChange = (next: string) => {
    const params = new URLSearchParams(searchParams)
    // The default view leaves no trace in the URL: "?view=me" and no query
    // string are the same screen, and only one of them should be shareable.
    if (next === 'me') params.delete('view')
    else params.set('view', next)
    setSearchParams(params, { replace: true })
  }

  const header = (
    <PageHeader
      title={user ? `Welcome back, ${user.firstName}` : 'Dashboard'}
      description={
        workspace ? `${workspace.workspaceName} · you are ${workspace.roleName} here` : undefined
      }
    />
  )

  // Neither dashboard is readable. Possible for a role granted nothing but
  // membership, and a real state rather than an error: the API would answer
  // 403, and saying so before asking is kinder than a red panel.
  if (!canSeeMine && !canSeeWorkspace) {
    return (
      <div className="space-y-6">
        {header}
        <EmptyState
          icon={LockIcon}
          title="No dashboard to show"
          description="Your role in this workspace does not include reading tasks or projects. An administrator can grant it."
        />
      </div>
    )
  }

  // Only one of the two is available, so a tab strip with a single tab in it
  // would be furniture rather than a choice.
  if (!canSeeWorkspace) {
    return (
      <div className="space-y-6">
        {header}
        <EmployeeDashboard />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {header}

      <Tabs value={view} onValueChange={onViewChange} className="space-y-4">
        <TabsList>
          <TabsTrigger value="me">My work</TabsTrigger>
          <TabsTrigger value="workspace">Workspace</TabsTrigger>
        </TabsList>

        <TabsContent value="me">
          {canSeeMine ? (
            <EmployeeDashboard />
          ) : (
            <EmptyState
              icon={LockIcon}
              title="Your own dashboard is not available"
              description="Reading your tasks needs the task:read permission in this workspace."
            />
          )}
        </TabsContent>

        <TabsContent value="workspace">
          <WorkspaceDashboard />
        </TabsContent>
      </Tabs>
    </div>
  )
}
