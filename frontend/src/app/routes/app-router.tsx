import { Navigate, Route, Routes } from 'react-router-dom'

import { AppLayout } from '@/components/layout/app-layout'
import { AuthLayout } from '@/components/layout/auth-layout'
import {
  AcceptInvitationPage,
  ForgotPasswordPage,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from '@/features/auth'
import {
  AccountsPage,
  AdminOverviewPage,
  AdminWorkspacesPage,
  PlatformActivityPage,
  PlatformProjectsPage,
  PlatformTeamsPage,
  RolesPage,
} from '@/features/admin'
import {
  AccountPasswordPage,
  AccountProfilePage,
  AccountSessionsPage,
} from '@/features/account'
import { DashboardPage } from '@/features/dashboard'
import { PeoplePage } from '@/features/people'
import { NotificationsPage } from '@/features/notifications'
import { ProjectDetailPage, ProjectsPage } from '@/features/projects'
import {
  OverdueReportPage,
  ProjectReportPage,
  ReportsOverviewPage,
  TaskReportPage,
  TeamReportPage,
  WorkloadReportPage,
} from '@/features/reports'
import { TaskDetailPage, TasksPage } from '@/features/tasks'
import { TeamDetailPage, TeamsPage } from '@/features/teams'
import { WorkspaceSettingsPage } from '@/features/workspace'
import { ForbiddenPage } from '@/pages/forbidden-page'
import { NotFoundPage } from '@/pages/not-found-page'
import { PlaceholderPage } from '@/pages/placeholder-page'

import { paths, workspaceRoutes } from './paths'
import { RequireAnonymous, RequireAuth, RequirePermission } from './route-guard'
import { WorkspaceIndexRedirect, WorkspaceRoute } from './workspace-route'

/**
 * The route table.
 *
 * Four groups: the public authentication screens, everything inside a
 * workspace, the platform-level admin panel, and the standalone status pages.
 *
 * The private half is nested under `/w/:workspaceSlug`, so the tenant the
 * backend enforces on every query is visible in the URL rather than implied by
 * a store. `WorkspaceRoute` turns that slug into the active workspace before
 * anything below it renders; the guards then check the session and the
 * permission codes, repeating server-side checks so the interface refuses
 * before a request is made.
 *
 * The admin panel deliberately sits outside the workspace segment. Its
 * endpoints are gated on a platform role and take no workspace, so scoping its
 * URL to one would misdescribe what it does.
 *
 * Most elements are still placeholders. Each is replaced by the feature that
 * owns it, which is why the structure is worth having now: the guards, the
 * layouts and the paths do not change when the screens arrive.
 *
 * Routes are declared eagerly for the moment. Code-splitting each feature with
 * `React.lazy` is the obvious next step, and the `Suspense` boundaries in both
 * layouts are already in place for it.
 */
export function AppRouter() {
  return (
    <Routes>
      {/* Public. A signed-in user is bounced to their workspace. */}
      <Route element={<RequireAnonymous />}>
        <Route element={<AuthLayout />}>
          <Route path={paths.auth.login} element={<LoginPage />} />
          <Route path={paths.auth.register} element={<RegisterPage />} />
          <Route path={paths.auth.forgotPassword} element={<ForgotPasswordPage />} />
          <Route path={paths.auth.resetPassword} element={<ResetPasswordPage />} />
        </Route>
      </Route>

      {/* Reachable with or without a session: both arrive from an emailed link. */}
      <Route element={<AuthLayout />}>
        <Route path={paths.auth.verifyEmail} element={<VerifyEmailPage />} />
        <Route path={paths.auth.acceptInvitation} element={<AcceptInvitationPage />} />
      </Route>

      {/* Private. */}
      <Route element={<RequireAuth />}>
        {/* No workspace in the URL: send the user to the one they were last in. */}
        <Route path={paths.root} element={<WorkspaceIndexRedirect />} />

        <Route path={paths.workspacePattern} element={<WorkspaceRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate to={workspaceRoutes.dashboard} replace />} />
            <Route path={workspaceRoutes.dashboard} element={<DashboardPage />} />

            <Route element={<RequirePermission codes={['project:read', 'project:read_any']} />}>
              <Route path={workspaceRoutes.projects} element={<ProjectsPage />} />
              <Route path={workspaceRoutes.project} element={<ProjectDetailPage />} />
            </Route>

            <Route element={<RequirePermission codes={['task:read']} />}>
              <Route path={workspaceRoutes.tasks} element={<TasksPage />} />
              <Route path={workspaceRoutes.myTasks} element={<TasksPage mine />} />
              <Route path={workspaceRoutes.task} element={<TaskDetailPage />} />
              {/* Every report is computed over the caller's project scope, so
                  `task:read` is the gate for all of them. Two of the screens
                  need more than that — the project report needs `project:read`
                  and the team report needs the workspace dashboard's three
                  codes — and each says so itself rather than being routed to
                  403, because losing one of those grants leaves the rest of
                  the section perfectly usable. */}
              <Route path={workspaceRoutes.reports} element={<ReportsOverviewPage />} />
              <Route path={workspaceRoutes.reportProjects} element={<ProjectReportPage />} />
              <Route path={workspaceRoutes.reportTasks} element={<TaskReportPage />} />
              <Route path={workspaceRoutes.reportOverdue} element={<OverdueReportPage />} />
              <Route path={workspaceRoutes.reportWorkload} element={<WorkloadReportPage />} />
              <Route path={workspaceRoutes.reportTeams} element={<TeamReportPage />} />
            </Route>

            <Route element={<RequirePermission codes={['team:read']} />}>
              <Route path={workspaceRoutes.teams} element={<TeamsPage />} />
              <Route path={workspaceRoutes.team} element={<TeamDetailPage />} />
            </Route>

            <Route element={<RequirePermission codes={['user:read', 'member:read']} />}>
              <Route path={workspaceRoutes.users} element={<PeoplePage />} />
              <Route path={workspaceRoutes.user} element={<PlaceholderPage title="Person" />} />
            </Route>

            <Route path={workspaceRoutes.notifications} element={<NotificationsPage />} />
            {/* Every member may read the settings; only an administrator gets
                the controls, which the screen decides rather than the router.
                Gating the route on `workspace:update` would hide the timezone
                from the people whose dates are computed in it. */}
            <Route path={workspaceRoutes.settings} element={<WorkspaceSettingsPage />} />
          </Route>
        </Route>

        {/* Your own account, outside every workspace. Its endpoints are gated
            on `isAuthenticated()` alone and take their subject from the
            security context rather than from a path, so there is no permission
            to check here and no workspace to scope to — being signed in is the
            whole of it. */}
        <Route element={<AppLayout />}>
          <Route path={paths.account.root} element={<AccountProfilePage />} />
          <Route path={paths.account.password} element={<AccountPasswordPage />} />
          <Route path={paths.account.sessions} element={<AccountSessionsPage />} />
        </Route>

        {/* The admin panel, outside every workspace and staying that way. Its
            endpoints are gated by `@perm.onPlatform`, which reads the caller's
            platform role and never consults workspace membership, and none of
            them takes a workspace as an authorization input — hence no
            workspace segment in the path.

            `platform` on the guard asks the same narrow question. The ordinary
            check answers on the union of platform and workspace grants, and a
            workspace administrator holds several of the codes used below inside
            their own workspace, so the union would open a door the API keeps
            shut. `admin:read_system` is granted to no workspace role, which
            makes it the right gate for the panel as a whole; each screen then
            re-checks the codes its own endpoints need. */}
        <Route element={<AppLayout />}>
          <Route element={<RequirePermission codes={['admin:read_system']} platform />}>
            <Route path={paths.admin.root} element={<AdminOverviewPage />} />
            <Route path={paths.admin.users} element={<AccountsPage />} />
            <Route path={paths.admin.workspaces} element={<AdminWorkspacesPage />} />
            <Route path={paths.admin.roles} element={<RolesPage />} />
            <Route path={paths.admin.projects} element={<PlatformProjectsPage />} />
            <Route path={paths.admin.teams} element={<PlatformTeamsPage />} />
            <Route path={paths.admin.activity} element={<PlatformActivityPage />} />
            {/* The statistics are the panel's own root rather than a screen of
                their own. Kept as a redirect so a bookmark still lands. */}
            <Route
              path={paths.admin.statistics}
              element={<Navigate to={paths.admin.root} replace />}
            />
          </Route>
        </Route>
      </Route>

      <Route path={paths.forbidden} element={<ForbiddenPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
