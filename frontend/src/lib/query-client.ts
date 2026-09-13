import { QueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api'

/**
 * Server state lives here, as docs/architecture.md specifies: TanStack Query
 * supplies the caching, pagination, and loading and error states the
 * requirements ask for, so no feature has to hand-roll them.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Long enough that moving between screens does not refetch, short
        // enough that a board left open goes stale rather than lying.
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        retry: (failureCount, error) => {
          // A refused or missing record does not become available by asking
          // again, and a rejected input never will. Only faults are retried.
          if (error instanceof ApiError) {
            if (error.isUnauthorised || error.isForbidden || error.isNotFound || error.isValidation) {
              return false
            }
          }
          return failureCount < 2
        },
        refetchOnWindowFocus: false,
      },
      mutations: {
        // A write is not safe to repeat on the client's own initiative.
        retry: false,
      },
    },
  })
}

/**
 * Query key roots, one per feature folder.
 *
 * Centralised so that invalidating "every task query" is a single, greppable
 * expression rather than a string literal repeated across features. Each
 * feature extends its own root with the arguments that identify a query.
 */
export const queryKeys = {
  auth: ['auth'] as const,
  users: ['users'] as const,
  workspaces: ['workspaces'] as const,
  projects: ['projects'] as const,
  tasks: ['tasks'] as const,
  teams: ['teams'] as const,
  notifications: ['notifications'] as const,
  dashboard: ['dashboard'] as const,
  reports: ['reports'] as const,
  admin: ['admin'] as const,
} as const
