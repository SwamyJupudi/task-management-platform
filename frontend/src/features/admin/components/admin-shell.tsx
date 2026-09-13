import type { ReactNode } from 'react'

import { PageHeader } from '@/components/common/page-header'

/**
 * The frame every admin screen sits in.
 *
 * A plain header rather than a navigation strip, because the sidebar's Platform
 * section already lists these six screens and a second set of links beside it
 * would be two places to keep in step.
 *
 * The description says "installation" rather than naming a workspace, which is
 * the one thing every screen here has in common and the distinction the panel
 * exists to make: nothing below is scoped to a tenant.
 */
export function AdminShell({
  title,
  description,
  actions,
  children,
}: {
  title: string
  description?: string
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="space-y-6">
      <PageHeader title={title} description={description} actions={actions} />
      {children}
    </div>
  )
}
