/**
 * The dashboard feature's public surface.
 *
 * The router needs the screen. The API calls and the panels are internals and
 * stay that way, so the feature is free to change shape without anything
 * outside it noticing.
 *
 * The one exception is the workspace dashboard query. Team performance is
 * returned by `GET /dashboard/workspace` rather than by any report endpoint, so
 * the team report reads it from here: same key, same cache, one request between
 * the two screens. A second copy of the call in the reports feature would fetch
 * the same body twice and let the two answers drift apart on screen. The table
 * that draws those rows travels with it, for the same reason.
 */

export { DashboardPage } from './pages/dashboard-page'
export { useWorkspaceDashboard } from './hooks'
export { TeamPerformanceTable } from './components/team-performance-table'
