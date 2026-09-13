import type { CountByKey, Distribution, ProjectProgress, TeamPerformance } from '@/types/reports'

/**
 * The wire shapes the report endpoints return, and the filters they accept.
 *
 * The four records both this feature and the dashboard read live in
 * `@/types/reports` and are re-exported here, so a report component has one
 * import for every shape it draws.
 *
 * The filter types below are not wire shapes: they are the parameters each
 * endpoint actually declares, written down so a screen cannot offer a control
 * the backend would ignore. Every one of them is read from and written to the
 * query string, which is what makes a filtered report a link somebody can send.
 */

export type { CountByKey, Distribution, ProjectProgress, TeamPerformance }

/** One late task, with enough beside it to act on without a second request. */
export interface OverdueTask {
  taskId: string
  key: string
  title: string
  projectId: string
  projectName: string
  /** Null when nobody holds it. */
  assigneeUserId: string | null
  assigneeName: string | null
  status: string
  priority: string
  dueDate: string
  /** Whole days past the date, counted in the workspace's timezone. */
  daysOverdue: number
}

/**
 * What one person is carrying, and what they finished in the period asked about.
 *
 * Effort is summed over open work only: a load is what is still on the desk,
 * and adding the minutes of everything ever finished would make the number grow
 * forever and stop describing anything.
 *
 * `fullName` is null for somebody whose account has since been removed. The row
 * is kept rather than dropped — their work still exists and still has to be
 * counted somewhere.
 */
export interface Workload {
  userId: string
  email: string | null
  fullName: string | null
  open: number
  inProgress: number
  overdue: number
  /** The one figure bounded by the window rather than by a status. */
  completedInPeriod: number
  estimatedMinutes: number
  actualMinutes: number
}

/**
 * One bucket of the productivity trend: what arrived and what was finished.
 *
 * Both lines, because either alone says nothing — completing forty tasks a week
 * is keeping up or falling behind entirely depending on how many arrived. Every
 * bucket in the window is returned, including the empty ones, so a quiet
 * fortnight draws a flat line rather than a gap.
 */
export interface TrendPoint {
  /** The day, the Monday of the week, or the first of the month. */
  bucketStart: string
  created: number
  completed: number
}

/** One team's own figures, and who is carrying them. */
export interface TeamDashboard {
  teamId: string
  name: string
  leadUserId: string | null
  memberCount: number
  projectCount: number
  openTasks: number
  overdueTasks: number
  completedTasks: number
  averageProgress: number
  /** One row per member, including those carrying nothing. */
  workload: Workload[]
  taskDistribution: Distribution
}

/**
 * The window every period-aware report is read over.
 *
 * Both ends are optional and both are inclusive dates. Naming only a start
 * means "from then until now"; naming only an end means "the usual window
 * ending there"; naming neither means the last thirty days. The backend
 * resolves all three in the workspace's own timezone, so nothing here computes
 * a default date — an interface in another timezone would resolve "today"
 * differently from the server and label the result with the server's answer.
 */
export interface Period {
  from?: string | undefined
  to?: string | undefined
}

/** `GET /reports/projects`. */
export interface ProjectReportFilters {
  /** Repeated, not comma-joined: Spring binds a repeated key to a List. */
  status?: string[] | undefined
  teamId?: string | undefined
  ownerUserId?: string | undefined
}

/** `GET /reports/tasks/distribution`, and the same three that `trends` takes. */
export interface TaskReportFilters extends Period {
  projectId?: string | undefined
  teamId?: string | undefined
  assigneeUserId?: string | undefined
}

/** `GET /reports/tasks/overdue`. No window: a late task is late today. */
export interface OverdueReportFilters {
  projectId?: string | undefined
  teamId?: string | undefined
  assigneeUserId?: string | undefined
}

/**
 * `GET /reports/workload`.
 *
 * No assignee filter, deliberately: the report is one row per person, so
 * narrowing it to one person is choosing a row rather than filtering the query,
 * and the endpoint does not accept it.
 */
export interface WorkloadReportFilters extends Period {
  projectId?: string | undefined
  teamId?: string | undefined
}

/** The three buckets `TrendGranularity` accepts. Absent means the server chooses. */
export type Granularity = 'DAY' | 'WEEK' | 'MONTH'
