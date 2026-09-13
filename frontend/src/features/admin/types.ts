/**
 * The wire shapes the admin panel exchanges.
 *
 * Each mirrors a record the backend already returns: `admin.dto` for the three
 * platform-wide ones, `users.dto`, `workspaces.dto` and `activity.dto` for the
 * rest. Nothing here is derived — a count, a percentage or a state the backend
 * computes is read rather than recomputed.
 *
 * Three of these records are the only ones in the whole interface that cross
 * workspaces. That is the admin panel's defining property and the reason its
 * screens live outside `/w/:workspaceSlug`: an installation-wide figure has no
 * workspace to be scoped to.
 */

/** Counts keyed by an enum value the backend spells. Every key is present, including zeros. */
export type CountsByKey = Record<string, number>

/**
 * The platform at a glance, across every workspace.
 *
 * `generatedAt` is the moment the figures were read — nothing is cached — so a
 * screen can say how fresh they are rather than implying they are live.
 *
 * There is deliberately nothing operational here: no uptime, no memory, no
 * request rate. Those belong to Actuator and the log platform, and a second,
 * worse copy of them on this screen would be read as authoritative.
 */
export interface SystemStatistics {
  generatedAt: string
  /** The trailing window `recent` was measured over. */
  windowDays: number
  accounts: {
    total: number
    byStatus: CountsByKey
    /** Locked out right now. A machine decision, distinct from DEACTIVATED. */
    locked: number
  }
  workspaces: {
    total: number
    byStatus: CountsByKey
    teams: number
    /** Every membership row; larger than the headcount when people belong to several. */
    memberships: number
  }
  work: {
    projects: number
    projectsByStatus: CountsByKey
    tasks: number
    tasksByStatus: CountsByKey
    /** Open work past its date. Measured in UTC, because this figure spans zones. */
    tasksOverdue: number
  }
  storage: {
    attachments: number
    /** Live attachments only, so the store is always at least this large. */
    totalBytes: number
  }
  recent: {
    accountsCreated: number
    accountsSignedIn: number
    auditRowsWritten: number
  }
}

/**
 * One account on the administrative directory.
 *
 * `lockedUntil` and `status` answer different questions and are shown as
 * different things: a lock is a temporary machine decision that expires, a
 * deactivation a durable human one that does not.
 *
 * `platformAdministrator` is a boolean rather than a role identifier, because
 * there is exactly one platform role and naming it would invite the belief that
 * there could be others.
 */
export interface PlatformAccount {
  id: string
  email: string
  firstName: string
  lastName: string
  /** `ACTIVE`, `PENDING_VERIFICATION`, `DEACTIVATED`. */
  status: string
  emailVerified: boolean
  platformAdministrator: boolean
  workspaceCount: number
  lockedUntil: string | null
  lastLoginAt: string | null
  createdAt: string
}

/** One project on the cross-workspace overview, with the workspace that owns it. */
export interface PlatformProject {
  id: string
  workspaceId: string
  /** The column that makes this listing readable; null only in a race the backend guards against. */
  workspaceName: string | null
  key: string
  name: string
  status: string
  ownerUserId: string | null
  teamId: string | null
  /** Derived from its tasks, 0 to 100. Read, never recomputed. */
  progress: number
  updatedAt: string
}

/** An account as the account endpoints return it after a write. */
export interface Account {
  id: string
  email: string
  firstName: string
  lastName: string
  status: string
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string
}

/** One entry of the global permission catalog. Written by migrations; read-only. */
export interface PermissionEntry {
  code: string
  resource: string
  action: string
  description: string
}

/** A role and the permission codes it grants. */
export interface Role {
  id: string
  slug: string
  name: string
  /** `WORKSPACE` here; a platform role is unreachable from any workspace path. */
  scope: string
  /** Seeded roles cannot be deleted. They can still be edited. */
  system: boolean
  permissions: string[]
}

/** A workspace, as the platform-wide listing returns it. */
export interface WorkspaceSummary {
  id: string
  name: string
  slug: string
  description: string | null
  timezone: string
  defaultRoleSlug: string
  /** `ACTIVE` or `ARCHIVED`. An archived workspace is frozen against every change. */
  status: string
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

/** `GET /admin/accounts`. */
export interface AccountFilters {
  search?: string | undefined
  status?: string | undefined
  /** Only accounts locked out right now. The question an administrator usually arrives with. */
  locked?: boolean | undefined
}

/** `GET /admin/projects`. A workspace here is a filter, never a scope. */
export interface PlatformProjectFilters {
  workspaceId?: string | undefined
  status?: string | undefined
  ownerUserId?: string | undefined
  teamId?: string | undefined
}

/** The body of `PATCH /users/{id}`. Name only: an address is an account's identity. */
export interface UpdateAccountInput {
  firstName: string
  lastName: string
}
