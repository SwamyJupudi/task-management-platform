import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { BrowserRouter } from 'react-router-dom'

import { Toaster } from '@/components/ui/sonner'
import { SessionGate } from '@/features/auth'
import { useTheme } from '@/hooks/use-theme'
import { createQueryClient } from '@/lib/query-client'

/**
 * Every context the application needs, composed once.
 *
 * The order matters: the router is outermost so that anything inside it may
 * navigate, and the query client sits inside it so a query may be cancelled
 * on a route change.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  // Created in state, not at module scope, so that a remount (and every
  // test) gets a clean cache rather than one shared across renders.
  const [queryClient] = useState(createQueryClient)

  useTheme()

  return (
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        {/* Inside the query client, because restoring a session and loading the
            active workspace's permissions are both queries. */}
        <SessionGate>{children}</SessionGate>
        <Toaster position="bottom-right" closeButton richColors />
      </QueryClientProvider>
    </BrowserRouter>
  )
}
