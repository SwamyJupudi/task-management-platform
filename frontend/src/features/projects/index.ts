/**
 * The projects feature's public surface.
 *
 * The router needs the two screens and nothing else. The hooks, the API calls,
 * the schemas and the panels are internals and stay that way, so the feature is
 * free to change shape without anything outside it noticing.
 */

export { ProjectsPage } from './pages/projects-page'
export { ProjectDetailPage } from './pages/project-detail-page'
