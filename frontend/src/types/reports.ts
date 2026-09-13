/**
 * The wire shapes the reporting endpoints share.
 *
 * Each mirrors a record in the Spring `reports.dto` package. Two features read
 * them — the dashboard summarises them and the reports screens page through
 * them — so they live here rather than inside either, and neither keeps a
 * second copy that could drift from the first.
 *
 * Nothing here is derived or reshaped. A figure the backend computes — a
 * percentage, a day count, a label — is read rather than recomputed, because
 * the platform has one rule for each of those and a copy on this side would
 * disagree with it the first time either changed.
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

/**
 * One team's line: its projects, and the work in them.
 *
 * Task counts are over the team's projects rather than over its members. A team
 * is responsible for the work in the projects it runs, including work assigned
 * to somebody borrowed from elsewhere, and excluding work its members do on
 * other teams' projects.
 */
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
