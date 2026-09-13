import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'

import { FullPageSpinner } from '@/components/common/full-page-spinner'
import { env } from '@/config/env'

/** The centred shell the sign-in, registration and recovery screens use. */
export function AuthLayout() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm space-y-6">
        <div className="space-y-1 text-center">
          <h1 className="text-lg font-semibold">{env.appName}</h1>
        </div>
        <Suspense fallback={<FullPageSpinner />}>
          <Outlet />
        </Suspense>
      </div>
    </div>
  )
}
