import {
  BellIcon,
  ChartNoAxesColumnIcon,
  FolderKanbanIcon,
  GaugeIcon,
  LayoutDashboardIcon,
  ListChecksIcon,
  ScrollTextIcon,
  SettingsIcon,
  ShieldIcon,
  UserCogIcon,
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
 *
 * `to` is a function of the workspace slug rather than a string, because every
 * destination inside a workspace is scoped to it. The platform section ignores
 * its argument: the admin panel belongs to no workspace.
 *
 * The codes on a platform entry are asked platform-wide rather than on the
 * union, because that is how the endpoints behind them are gated. Several of
 * them — `user:read`, `role:read`, `team:read` — are also held by the seeded
 * workspace administrator, so asking the union here would put the admin panel
 * in the sidebar of somebody the API would refuse.
 */
export interface NavItem {
  label: string
  to: (workspaceSlug: string) => string
  icon: LucideIcon
  /** Any one of these is enough. Empty means no permission needed. */
  permissions: readonly string[]
  /**
   * Match this path exactly rather than as a prefix.
   *
   * Set on entries whose children are separate destinations, so that opening
   * one project does not leave two rail entries looking active at once.
   */
  end?: boolean
}

export interface NavSection {
  id: string
  /** Rendered as the group heading, and as the rail's accessible label. */
  label: string
  items: readonly NavItem[]
  /** Restricts the whole section to accounts holding a platform role. */
  platformOnly?: boolean
}

const workspaceItems: readonly NavItem[] = [
  {
    label: 'Dashboard',
    to: paths.workspace.dashboard,
    icon: LayoutDashboardIcon,
    permissions: [],
    end: true,
  },
  {
    label: 'Projects',
    to: paths.workspace.projects,
    icon: FolderKanbanIcon,
    permissions: ['project:read', 'project:read_any'],
  },
  {
    label: 'Tasks',
    to: paths.workspace.tasks,
    icon: ListChecksIcon,
    permissions: ['task:read'],
  },
  {
    label: 'Teams',
    to: paths.workspace.teams,
    icon: UsersRoundIcon,
    permissions: ['team:read'],
  },
  {
    label: 'People',
    to: paths.workspace.users,
    icon: UsersIcon,
    permissions: ['user:read', 'member:read'],
  },
  {
    label: 'Reports',
    to: paths.workspace.reports,
    icon: ChartNoAxesColumnIcon,
    // The reports module has no permission code of its own: ReportAccessGuard
    // gates on task:read and narrows the aggregate by what the caller can see.
    permissions: ['task:read'],
  },
]

const workspaceAccountItems: readonly NavItem[] = [
  {
    label: 'Notifications',
    to: paths.workspace.notifications,
    icon: BellIcon,
    permissions: [],
  },
  {
    label: 'Settings',
    to: paths.workspace.settings,
    icon: SettingsIcon,
    permissions: [],
  },
]

const platformItems: readonly NavItem[] = [
  {
    label: 'Overview',
    to: () => paths.admin.root,
    icon: GaugeIcon,
    permissions: ['admin:read_system'],
    end: true,
  },
  {
    label: 'Accounts',
    to: () => paths.admin.users,
    icon: UserCogIcon,
    permissions: ['user:read'],
  },
  {
    label: 'Roles',
    to: () => paths.admin.roles,
    icon: ShieldIcon,
    permissions: ['role:read'],
  },
  {
    label: 'All projects',
    to: () => paths.admin.projects,
    icon: FolderKanbanIcon,
    permissions: ['admin:read_system'],
  },
  {
    label: 'All teams',
    to: () => paths.admin.teams,
    icon: UsersRoundIcon,
    permissions: ['team:read'],
  },
  {
    label: 'Audit log',
    to: () => paths.admin.activity,
    icon: ScrollTextIcon,
    permissions: ['activity:read'],
  },
]

/** Sections scoped to a workspace. Hidden entirely when there is not one. */
export const workspaceNav: readonly NavSection[] = [
  { id: 'workspace', label: 'Workspace', items: workspaceItems },
  { id: 'account', label: 'You', items: workspaceAccountItems },
]

/** Sections that belong to the platform rather than to any one workspace. */
export const platformNav: readonly NavSection[] = [
  { id: 'platform', label: 'Platform', items: platformItems, platformOnly: true },
]
