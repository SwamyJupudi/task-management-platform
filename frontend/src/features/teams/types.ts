/**
 * The wire shapes the team endpoints exchange.
 *
 * Each mirrors a record in the Spring `teams.dto` package.
 */

/** The two lifecycle states a team has. Archiving is reversible. */
export type TeamStatus = 'ACTIVE' | 'ARCHIVED'

/**
 * A team, with its lead resolved so a list needs no second call per row.
 *
 * The lead's fields are null when the team has none, which is an ordinary state
 * rather than an error: a team between leads still exists and still has
 * members.
 */
export interface Team {
  id: string
  workspaceId: string
  name: string
  description: string | null
  leadUserId: string | null
  leadEmail: string | null
  leadName: string | null
  status: TeamStatus
  memberCount: number
  createdAt: string
  updatedAt: string
}

/** One person's place on a team. `lead` is derived from the team. */
export interface TeamMember {
  userId: string
  email: string
  firstName: string
  lastName: string
  lead: boolean
  userStatus: string
  joinedAt: string
}

/** The body of `POST /workspaces/{id}/teams`. A named lead joins the team too. */
export interface CreateTeamInput {
  name: string
  description?: string | undefined
  leadUserId?: string | undefined
}

/**
 * The body of `PATCH /teams/{id}`. Omitted fields are left alone.
 *
 * No status and no lead: archiving has its own two endpoints because it is a
 * lifecycle decision, and naming a lead also puts that person in the team.
 */
export interface UpdateTeamInput {
  name?: string | undefined
  description?: string | undefined
}
