/**
 * Every route path in one place.
 *
 * Links are built from these rather than from string literals, so renaming a
 * route is a change here instead of a search across the codebase, and a typo
 * is a type error rather than a dead link.
 */
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

  app: {
    dashboard: '/dashboard',

    projects: '/projects',
    project: (projectId: string) => `/projects/${projectId}`,

    tasks: '/tasks',
    task: (taskId: string) => `/tasks/${taskId}`,
    myTasks: '/tasks/mine',

    teams: '/teams',
    team: (teamId: string) => `/teams/${teamId}`,

    users: '/users',
    user: (userId: string) => `/users/${userId}`,

    notifications: '/notifications',
    reports: '/reports',
    settings: '/settings',
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
