/**
 * The wire shapes the project endpoints exchange.
 *
 * Each mirrors a record in the Spring `projects.dto` package. Nothing is
 * reshaped on the way in: `progress` is the derived column the projects module
 * maintains, `memberCount` is counted server-side, and both are read rather
 * than recomputed, because the platform defines each of them once.
 */

/** The five lifecycle states, as the backend's `ProjectStatus` enum spells them. */
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'

/** The four levels the requirements name, shared with tasks. */
export type ProjectPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/**
 * A project, with the owner and team already resolved.
 *
 * The owner fields are null when the project has no owner, which is an
 * ordinary state rather than an error: a project between owners still exists
 * and still has members. The same is true of the team.
 */
export interface Project {
  id: string
  workspaceId: string
  key: string
  name: string
  description: string | null
  ownerUserId: string | null
  ownerEmail: string | null
  ownerName: string | null
  teamId: string | null
  teamName: string | null
  status: ProjectStatus
  priority: ProjectPriority
  startDate: string | null
  endDate: string | null
  labels: string[]
  /** Share of work complete, 0 to 100. Derived from tasks by the backend. */
  progress: number
  memberCount: number
  createdAt: string
  updatedAt: string
}

/** One person's place on a project. `owner` is derived from the project. */
export interface ProjectMember {
  userId: string
  email: string
  firstName: string
  lastName: string
  owner: boolean
  userStatus: string
  joinedAt: string
}

/** The body of `POST /projects`. */
export interface CreateProjectInput {
  key: string
  name: string
  description?: string | undefined
  ownerUserId?: string | undefined
  teamId?: string | undefined
  priority?: string | undefined
  startDate?: string | undefined
  endDate?: string | undefined
  labels?: string[] | undefined
}

/**
 * The body of `PATCH /projects/{id}`. Every field optional; omitted is
 * left alone.
 *
 * No `status` and no `ownerUserId`: each has its own endpoint, because a
 * transition is checked against the state machine and naming an owner also
 * adjusts the roster. Neither should happen as a side effect of a general edit.
 */
export interface UpdateProjectInput {
  name?: string | undefined
  description?: string | undefined
  teamId?: string | undefined
  /** Send true to detach the project from its team. */
  clearTeam?: boolean | undefined
  priority?: string | undefined
  startDate?: string | null | undefined
  endDate?: string | null | undefined
  /** Replaces the whole set; an empty list clears it. */
  labels?: string[] | undefined
}

/** Everything the list endpoint narrows on. */
export interface ProjectFilters {
  status?: ProjectStatus | undefined
  priority?: ProjectPriority | undefined
  teamId?: string | undefined
  ownerUserId?: string | undefined
  /** Matches the name or the key, case-insensitively. */
  q?: string | undefined
  label?: string | undefined
}

/** A page request, in the shape the shared client sends it. */
export interface ProjectPageRequest {
  page: number
  size: number
  /** `field,direction`, restricted to the backend's allowlist. */
  sort: string
}

/** A team, as the picker and the team filter need it. */
export interface TeamOption {
  id: string
  name: string
  leadUserId: string | null
  status: string
}

/** A workspace member, as the owner and member pickers need them. */
export interface WorkspaceMemberOption {
  userId: string
  email: string
  firstName: string
  lastName: string
  roleSlug: string
  userStatus: string
}
