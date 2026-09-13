import type { ReactNode } from 'react'

import { PageHeader } from '@/components/common/page-header'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'

import { ReportNav } from './report-nav'

/**
 * The frame every report screen sits in: a title, the strip of reports, a body.
 *
 * One component so the navigation stays in the same place on all six screens
 * and moving between them does not shift the page under the reader. The title
 * names the report rather than the section, because the breadcrumb trail and
 * the strip already say which section this is.
 *
 * The workspace name goes in the description, since a report is always of one
 * workspace and reading the wrong workspace's figures is the mistake worth
 * making impossible to make quietly.
 */
export function ReportShell({
  title,
  description,
  actions,
  children,
}: {
  title: string
  /** What the report is of. The workspace name is appended for you. */
  description?: string
  actions?: ReactNode
  children: ReactNode
}) {
  const workspace = useActiveWorkspace()

  const suffix = workspace ? `In ${workspace.workspaceName}` : undefined
  const full = description ? (suffix ? `${description} · ${suffix}` : description) : suffix

  return (
    <div className="space-y-6">
      <PageHeader title={title} description={full} actions={actions} />
      <ReportNav />
      {children}
    </div>
  )
}
