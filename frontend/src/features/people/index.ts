/**
 * The people feature's public surface.
 *
 * The router needs the directory. The two lookups are exported as well, because
 * this feature owns the workspace roster and its roles: anything that needs to
 * put a colleague in a dropdown should reach for these rather than grow its own
 * copy of the same call, which is what happened three times before this feature
 * existed.
 */

export { PeoplePage } from './pages/people-page'
export { useWorkspaceMembers, useWorkspaceRoles, usePeoplePermissions } from './hooks'
export type { WorkspaceMember, WorkspaceRole } from './types'
