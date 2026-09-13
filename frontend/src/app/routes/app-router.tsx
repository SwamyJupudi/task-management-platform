import { Navigate, Route, Routes } from 'react-router-dom'

import { AppLayout } from '@/components/layout/app-layout'
import { AuthLayout } from '@/components/layout/auth-layout'
import { ForbiddenPage } from '@/pages/forbidden-page'
import { NotFoundPage } from '@/pages/not-found-page'
import { PlaceholderPage } from '@/pages/placeholder-page'

import { paths } from './paths'
import { RequireAnonymous, RequireAuth, RequirePermission } from './route-guard'

/**
 * The route table.
 *
 * Three groups: the public authentication screens, the private application
 * shell, and the standalone status pages. Every private route sits under
 * `RequireAuth`, and the ones the API gates on a permission repeat that code
 * here so the interface refuses before the request is made.
 *
 * The elements are placeholders. Each is replaced by the feature that owns
 * it, which is why the structure is worth having now: the guards, the
 * layouts and the paths do not change when the screens arrive.
 *
 * Routes are declared eagerly for the moment. Code-splitting each feature
 * with `React.lazy` is the obvious next step, and the `Suspense` boundaries
 * in both layouts are already in place for it.
 */
export function AppRouter() {
  return (
    <Routes>
      {/* Public. A signed-in user is bounced to the dashboard. */}
      <Route element={<RequireAnonymous />}>
        <Route element={<AuthLayout />}>
          <Route path={paths.auth.login} element={<PlaceholderPage title="Sign in" />} />
          <Route path={paths.auth.register} element={<PlaceholderPage title="Create an account" />} />
          <Route
            path={paths.auth.forgotPassword}
            element={<PlaceholderPage title="Forgot your password" />}
          />
          <Route
            path={paths.auth.resetPassword}
            element={<PlaceholderPage title="Choose a new password" />}
          />
        </Route>
      </Route>

      {/* Reachable with or without a session: both arrive from an emailed link. */}
      <Route element={<AuthLayout />}>
        <Route path={paths.auth.verifyEmail} element={<PlaceholderPage title="Verify your email" />} />
        <Route
          path={paths.auth.acceptInvitation}
          element={<PlaceholderPage title="Accept your invitation" />}
        />
      </Route>

      {/* Private. */}
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to={paths.app.dashboard} replace />} />
          <Route path={paths.app.dashboard} element={<PlaceholderPage title="Dashboard" />} />

          <Route element={<RequirePermission codes={['project:read', 'project:read_any']} />}>
            <Route path={paths.app.projects} element={<PlaceholderPage title="Projects" />} />
            <Route path="/projects/:projectId" element={<PlaceholderPage title="Project" />} />
          </Route>

          <Route element={<RequirePermission codes={['task:read']} />}>
            <Route path={paths.app.tasks} element={<PlaceholderPage title="Tasks" />} />
            <Route path={paths.app.myTasks} element={<PlaceholderPage title="My tasks" />} />
            <Route path="/tasks/:taskId" element={<PlaceholderPage title="Task" />} />
            <Route path={paths.app.reports} element={<PlaceholderPage title="Reports" />} />
          </Route>

          <Route element={<RequirePermission codes={['team:read']} />}>
            <Route path={paths.app.teams} element={<PlaceholderPage title="Teams" />} />
            <Route path="/teams/:teamId" element={<PlaceholderPage title="Team" />} />
          </Route>

          <Route element={<RequirePermission codes={['user:read', 'member:read']} />}>
            <Route path={paths.app.users} element={<PlaceholderPage title="People" />} />
            <Route path="/users/:userId" element={<PlaceholderPage title="Person" />} />
          </Route>

          <Route path={paths.app.notifications} element={<PlaceholderPage title="Notifications" />} />
          <Route path={paths.app.settings} element={<PlaceholderPage title="Settings" />} />

          {/* The admin panel. Every endpoint behind it is gated on a platform
              permission, which only a platform role holds. */}
          <Route element={<RequirePermission codes={['admin:read_system']} />}>
            <Route path={paths.admin.root} element={<PlaceholderPage title="Admin" />} />
            <Route path={paths.admin.users} element={<PlaceholderPage title="User management" />} />
            <Route path={paths.admin.roles} element={<PlaceholderPage title="Roles and permissions" />} />
            <Route path={paths.admin.activity} element={<PlaceholderPage title="Activity log" />} />
            <Route path={paths.admin.statistics} element={<PlaceholderPage title="System statistics" />} />
          </Route>
        </Route>
      </Route>

      <Route path={paths.forbidden} element={<ForbiddenPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
