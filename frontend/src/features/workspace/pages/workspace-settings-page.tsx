import { LockIcon } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

import { GeneralSettingsForm } from '../components/general-settings-form'
import { LifecycleCard } from '../components/lifecycle-card'
import { useWorkspacePermissions, useWorkspaceSettings } from '../hooks'

/**
 * One workspace's settings.
 *
 * Reachable by every member, because every seeded role holds `workspace:read`
 * and the settings are worth seeing even when they cannot be changed: the
 * timezone is what every due date and every report window on the reader's
 * screen is computed in. Only an administrator gets the controls.
 *
 * There is no permission-denied screen for the workspace itself. Being here at
 * all means a membership, and a membership means `workspace:read`; the state
 * that does not resolve to a form is the settings failing to load, which the
 * error panel covers.
 */
export function WorkspaceSettingsPage() {
  const settings = useWorkspaceSettings()
  const { canUpdate } = useWorkspacePermissions()

  const workspace = settings.data
  const archived = workspace?.status === 'ARCHIVED'

  return (
    <div className="space-y-6">
      <PageHeader
        title="Workspace settings"
        description={workspace ? `${workspace.name} · ${workspace.slug}` : undefined}
      />

      <div className="max-w-2xl space-y-6">
        {settings.isError ? (
          <ErrorState error={settings.error} onRetry={() => void settings.refetch()} />
        ) : settings.isPending ? (
          <LoadingState label="Loading workspace settings" />
        ) : workspace ? (
          <>
            {archived ? (
              <Alert>
                <AlertTitle>This workspace is archived</AlertTitle>
                <AlertDescription>
                  <p>
                    Its settings and its contents stay readable, and nothing in it can be changed
                    until it is restored.
                  </p>
                </AlertDescription>
              </Alert>
            ) : null}

            {!canUpdate ? (
              <EmptyState
                icon={LockIcon}
                title="You can see these settings but not change them"
                description="Editing a workspace needs workspace:update, which its administrators hold."
                className="py-8"
              />
            ) : null}

            <GeneralSettingsForm workspace={workspace} />
            <LifecycleCard workspace={workspace} />
          </>
        ) : null}
      </div>
    </div>
  )
}
