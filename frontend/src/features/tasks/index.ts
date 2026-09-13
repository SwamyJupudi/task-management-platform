/**
 * The tasks feature's public surface.
 *
 * The router needs the two screens and nothing else. My Tasks is the listing
 * with its `mine` flag set rather than a third component, because it is the
 * same screen with the assignee pinned to the signed-in person.
 */

export { TasksPage } from './pages/tasks-page'
export { TaskDetailPage } from './pages/task-detail-page'
