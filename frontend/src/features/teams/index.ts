/**
 * The teams feature's public surface.
 *
 * The router needs the two screens. The hooks, the API calls and the panels are
 * internals and stay that way.
 *
 * The wire shape is exported as well, because the admin panel's cross-workspace
 * team overview reads exactly these rows from exactly this endpoint. A second
 * hand-written copy of the record would disagree with this one the first time
 * either was edited.
 */

export { TeamsPage } from './pages/teams-page'
export { TeamDetailPage } from './pages/team-detail-page'
export type { Team, TeamStatus } from './types'
