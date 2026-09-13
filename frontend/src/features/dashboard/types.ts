/**
 * The wire shapes the dashboard endpoints return.
 *
 * Each mirrors a record in the Spring `reports.dto` package. Nothing here is
 * derived or reshaped: a field that the backend computes — a percentage, a day
 * count, a label — is read rather than recomputed, because the platform has one
 * rule for each of those and a second copy here would disagree with it the
 * first time either changed.
 *
 * The shapes the reports feature reads as well live in `@/types/reports` and
 * are re-exported below, so a component importing from here does not have to
 * know which of the two owns a given record.
 */

import type {
  CountByKey,
  Distribution,
  ProjectProgress,
  TeamPerformance,
} from '@/types/reports'

/**
 * The four shapes the reports module also returns.
 *
 * Re-exported rather than redeclared. The dashboard summarises the same records
 * the report screens page through, and two hand-written copies of one wire
 * shape disagree the first time either is edited. Imported as well as
 * re-exported, because the composite bodies below are written in terms of them.
 */
export type { CountByKey, Distribution, ProjectProgress, TeamPerformance }

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
