/**
 * Every route path in one place.
 *
 * Links are built from these rather than from string literals, so renaming a
 * route is a change here instead of a search across the codebase, and a typo
 * is a type error rather than a dead link.
 *
 * Almost everything a signed-in user reaches lives under `/w/:workspaceSlug`.
 * The workspace is the tenant boundary the backend enforces on every query, so
 * putting it in the URL rather than only in a store means a link is complete:
 * it survives a reload, a bookmark and a paste into somebody else's chat
 * window, and two tabs can sit in two different workspaces without fighting
 * over one global "current workspace".
 *
 * The admin panel is the exception. It is gated on a platform role and its
 * endpoints take no workspace, so scoping it to one would be a lie.
 */

/** Slugs come from the API, but they are still interpolated into a URL. */
const workspaceRoot = (slug: string) => `/w/${encodeURIComponent(slug)}`

/**
 * The child paths the router declares, relative to `/w/:workspaceSlug`.
 *
 * Kept beside the builders below so a route and the link to it cannot drift
 * apart. React Router ranks a static segment above a dynamic one, so
 * `tasks/mine` is matched before `tasks/:taskId` whatever the order here.
 */
export const workspaceRoutes = {
  dashboard: 'dashboard',
  projects: 'projects',
  project: 'projects/:projectId',
  tasks: 'tasks',
  myTasks: 'tasks/mine',
  task: 'tasks/:taskId',
  teams: 'teams',
  team: 'teams/:teamId',
  users: 'users',
  user: 'users/:userId',
  notifications: 'notifications',
  reports: 'reports',
  // Each report is its own route rather than a tab, so a narrowed report is a
  // link somebody can send and the back button steps between them.
  reportProjects: 'reports/projects',
  reportTasks: 'reports/tasks',
  reportOverdue: 'reports/overdue',
  reportWorkload: 'reports/workload',
  reportTeams: 'reports/teams',
  settings: 'settings',
} as const

export const paths = {
  root: '/',

  auth: {
    login: '/login',
    register: '/register',
    forgotPassword: '/forgot-password',
    resetPassword: '/reset-password',
    verifyEmail: '/verify-email',
    acceptInvitation: '/invitations/accept',
  },

  /** The pattern the router declares. Not a link target: it has no slug in it. */
  workspacePattern: '/w/:workspaceSlug',

  workspace: {
    root: workspaceRoot,
    dashboard: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.dashboard}`,

    projects: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.projects}`,
    project: (slug: string, projectId: string) =>
      `${workspaceRoot(slug)}/${workspaceRoutes.projects}/${projectId}`,

    tasks: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.tasks}`,
    myTasks: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.myTasks}`,
    task: (slug: string, taskId: string) =>
      `${workspaceRoot(slug)}/${workspaceRoutes.tasks}/${taskId}`,

    teams: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.teams}`,
    team: (slug: string, teamId: string) =>
      `${workspaceRoot(slug)}/${workspaceRoutes.teams}/${teamId}`,

    users: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.users}`,
    user: (slug: string, userId: string) =>
      `${workspaceRoot(slug)}/${workspaceRoutes.users}/${userId}`,

    notifications: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.notifications}`,
    reports: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.reports}`,
    reportProjects: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.reportProjects}`,
    reportTasks: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.reportTasks}`,
    reportOverdue: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.reportOverdue}`,
    reportWorkload: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.reportWorkload}`,
    reportTeams: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.reportTeams}`,
    settings: (slug: string) => `${workspaceRoot(slug)}/${workspaceRoutes.settings}`,
  },

  admin: {
    root: '/admin',
    users: '/admin/users',
    roles: '/admin/roles',
    activity: '/admin/activity',
    statistics: '/admin/statistics',
  },

  forbidden: '/403',
  notFound: '/404',
} as const
