/**
 * The wire shapes the dashboard endpoints return.
 *
 * Each mirrors a record in the Spring `reports.dto` package. Nothing here is
 * derived or reshaped: a field that the backend computes — a percentage, a day
 * count, a label — is read rather than recomputed, because the platform has one
 * rule for each of those and a second copy here would disagree with it the
 * first time either changed.
 */

/**
 * One column of a breakdown.
 *
 * A list rather than a map keyed by status, because the order of a status
 * breakdown is the order of the board and an object has none. `label` is the
 * key written for a person, so the interface never needs to know the platform's
 * enums to draw a chart. Every column is present, including those nothing
 * holds, so a chart never has to guess the full set.
 */
export interface CountByKey {
  key: string
  label: string
  count: number
}

/** Task counts by status and by priority. Each list sums to `total`. */
export interface Distribution {
  byStatus: CountByKey[]
  byPriority: CountByKey[]
  total: number
}

/** One project's derived progress beside its task counts. */
export interface ProjectProgress {
  projectId: string
  key: string
  name: string
  status: string
  /** The derived percentage the projects module maintains, 0 to 100. */
  progress: number
  totalTasks: number
  doneTasks: number
  overdueTasks: number
  teamId: string | null
  ownerUserId: string | null
}

/** One team's line on the workspace dashboard. */
export interface TeamPerformance {
  teamId: string
  name: string
  leadUserId: string | null
  memberCount: number
  projectCount: number
  openTasks: number
  overdueTasks: number
  completedTasks: number
  /** Mean of the derived progress column across the team's projects. */
  averageProgress: number
}

/** One of the caller's own tasks due inside the lead window. */
export interface UpcomingDeadline {
  taskId: string
  key: string
  title: string
  projectId: string
  priority: string
  dueDate: string
  /** Whole days until due, in the workspace's timezone. Zero means today. */
  daysRemaining: number
}

/** One recorded action, as the audit trail reads it back. */
export interface ActivityEntry {
  id: string
  workspaceId: string
  actorUserId: string | null
  actorEmail: string | null
  actorName: string | null
  action: string
  entityType: string
  entityId: string
  projectId: string | null
  metadata: Record<string, unknown>
  /** Composed on read, never stored. Render this rather than building a line. */
  summary: string
  requestId: string | null
  createdAt: string
}

/** The body of `GET /workspaces/{id}/dashboard/workspace`. */
export interface WorkspaceDashboard {
  totalMembers: number
  /** Headcount by role slug. Roles nobody holds are present at zero. */
  membersByRole: Record<string, number>
  totalTeams: number
  activeProjects: number
  completedProjects: number
  openTasks: number
  overdueTasks: number
  taskDistribution: Distribution
  /** Capped and most recently touched first: a panel, not a listing. */
  projectProgress: ProjectProgress[]
  teamPerformance: TeamPerformance[]
}

/** The body of `GET /workspaces/{id}/dashboard/me`. */
export interface EmployeeDashboard {
  myProjects: ProjectProgress[]
  myTaskCounts: Distribution
  overdueCount: number
  upcomingDeadlines: UpcomingDeadline[]
  /** What the caller themselves did. Not what happened around them. */
  recentActivity: ActivityEntry[]
  myOpenSubtasks: number
}

/**
 * How many projects sit in each lifecycle state.
 *
 * Not a shape the backend returns. No endpoint answers it, so it is assembled
 * from one count per status — see `api.ts` for why that is a fair trade rather
 * than a workaround.
 */
export interface ProjectStatusCount {
  status: string
  label: string
  count: number
}
