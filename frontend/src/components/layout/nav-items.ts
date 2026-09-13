import {
  BellIcon,
  ChartNoAxesColumnIcon,
  FolderKanbanIcon,
  LayoutDashboardIcon,
  ListChecksIcon,
  ShieldIcon,
  UsersIcon,
  UsersRoundIcon,
  type LucideIcon,
} from 'lucide-react'

import { paths } from '@/app/routes/paths'

/**
 * The primary navigation, declared as data.
 *
 * Each entry names the permission codes that make it worth showing. The
 * sidebar filters on them, so a user is not offered a screen the API would
 * refuse. An entry with no codes is visible to anyone with a session.
 */
export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** Any one of these is enough. Empty means no permission needed. */
  permissions: readonly string[]
  /** Restricts the entry to accounts holding a platform role. */
  platformOnly?: boolean
}

export const primaryNav: readonly NavItem[] = [
  {
    label: 'Dashboard',
    to: paths.app.dashboard,
    icon: LayoutDashboardIcon,
    permissions: [],
  },
  {
    label: 'Projects',
    to: paths.app.projects,
    icon: FolderKanbanIcon,
    permissions: ['project:read', 'project:read_any'],
  },
  {
    label: 'Tasks',
    to: paths.app.tasks,
    icon: ListChecksIcon,
    permissions: ['task:read'],
  },
  {
    label: 'Teams',
    to: paths.app.teams,
    icon: UsersRoundIcon,
    permissions: ['team:read'],
  },
  {
    label: 'People',
    to: paths.app.users,
    icon: UsersIcon,
    permissions: ['user:read', 'member:read'],
  },
  {
    label: 'Notifications',
    to: paths.app.notifications,
    icon: BellIcon,
    permissions: [],
  },
  {
    label: 'Reports',
    to: paths.app.reports,
    icon: ChartNoAxesColumnIcon,
    // The reports module has no permission code of its own: ReportAccessGuard
    // gates on task:read and narrows the aggregate by what the caller can see.
    permissions: ['task:read'],
  },
]

export const adminNav: readonly NavItem[] = [
  {
    label: 'Admin',
    to: paths.admin.root,
    icon: ShieldIcon,
    // Gated on the platform role rather than a code; the admin endpoints use
    // @perm.onPlatform('admin:read_system'), which only a platform role holds.
    permissions: [],
    platformOnly: true,
  },
]
