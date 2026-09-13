/**
 * The sort allowlists, the caps and the closed sets the admin endpoints enforce.
 *
 * Copied from `AdminSorts`, `AdminProperties`, `UserStatus` and `ProjectStatus`
 * because no endpoint publishes them. Every one is checked server-side — an
 * unknown sort field answers 400 and a page over the cap is refused rather than
 * clamped — so this exists to avoid offering a control that would fail, never to
 * decide what is allowed.
 *
 * Keep in step with `AdminSorts`, `AdminProperties`, `UserStatus` and
 * `ProjectStatus`.
 */

/** `AdminProperties.maxPageSize` is 100; twenty is the platform's page. */
export const PAGE_SIZE = 20

/** The audit trail's own page. A history is scanned rather than paged through. */
export const ACTIVITY_PAGE_SIZE = 50

/** `AdminProperties.statsWindowDays`, for labelling a window nobody chose. */
export const DEFAULT_WINDOW_DAYS = 30

/** `AdminProperties.maxStatsWindowDays`. A wider window is refused, not truncated. */
export const MAX_WINDOW_DAYS = 366

interface Option {
  value: string
  label: string
}

/**
 * The windows the statistics screen offers.
 *
 * A short list rather than a free number field. Every value here is inside the
 * backend's bounds, so the control cannot produce the 400 that a typed number
 * could, and these are the four stretches anybody actually asks about.
 */
export const WINDOW_OPTIONS: readonly { value: number; label: string }[] = [
  { value: 7, label: 'Last 7 days' },
  { value: 30, label: 'Last 30 days' },
  { value: 90, label: 'Last 90 days' },
  { value: 365, label: 'Last year' },
]

export function isWindowOption(value: number): boolean {
  return WINDOW_OPTIONS.some((option) => option.value === value)
}

/** `AdminSorts.ACCOUNTS`, with the endpoint's own default first. */
export const ACCOUNT_SORTS: readonly Option[] = [
  { value: 'createdAt,desc', label: 'Newest accounts' },
  { value: 'createdAt,asc', label: 'Oldest accounts' },
  { value: 'lastLoginAt,desc', label: 'Recently active' },
  { value: 'lastLoginAt,asc', label: 'Longest silent' },
  { value: 'email,asc', label: 'Email A to Z' },
  { value: 'lastName,asc', label: 'Surname A to Z' },
  { value: 'firstName,asc', label: 'First name A to Z' },
  { value: 'status,asc', label: 'Status' },
]

export const DEFAULT_ACCOUNT_SORT = 'createdAt,desc'

/**
 * `AdminSorts.PROJECTS`.
 *
 * `workspaceName` is absent even though the response carries it: the name is
 * resolved after the page is fetched, so sorting by it would order each page
 * independently of the others, which is worse than not offering it.
 */
export const PROJECT_SORTS: readonly Option[] = [
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'createdAt,desc', label: 'Newest projects' },
  { value: 'progress,desc', label: 'Furthest along' },
  { value: 'progress,asc', label: 'Least progress' },
  { value: 'name,asc', label: 'Name A to Z' },
  { value: 'key,asc', label: 'Key' },
  { value: 'status,asc', label: 'Status' },
]

export const DEFAULT_PROJECT_SORT = 'updatedAt,desc'

/**
 * The account lifecycle, mirroring `UserStatus`.
 *
 * A locked account is not one of these. A lock is a temporary machine decision
 * recorded in a different column, which is why the directory filters on it
 * separately rather than offering it as a fourth status.
 */
export const ACCOUNT_STATUSES: readonly Option[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PENDING_VERIFICATION', label: 'Awaiting verification' },
  { value: 'DEACTIVATED', label: 'Deactivated' },
]

export function isAccountStatus(value: string): boolean {
  return ACCOUNT_STATUSES.some((status) => status.value === value)
}

/** The project lifecycle, mirroring `ProjectStatus`. */
export const PROJECT_STATUSES: readonly Option[] = [
  { value: 'PLANNING', label: 'Planning' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_HOLD', label: 'On hold' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export function isProjectStatus(value: string): boolean {
  return PROJECT_STATUSES.some((status) => status.value === value)
}

/** The two states a team has. Archiving is reversible. */
export const TEAM_STATUSES: readonly Option[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export function isTeamStatus(value: string): boolean {
  return TEAM_STATUSES.some((status) => status.value === value)
}

/** How many workspaces the pickers ask for. An installation's list is not long. */
export const WORKSPACE_OPTION_SIZE = 100

/**
 * An enum value written for a person.
 *
 * Used where the backend returns a raw key with no label beside it — the
 * statistics maps, and the status columns. Anything unrecognised still reads as
 * something rather than being hidden, so a state added later does not vanish
 * from a chart.
 */
export function humanise(value: string): string {
  const words = value.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/**
 * A byte count, written the way a storage figure is read.
 *
 * Decimal units rather than binary, matching what an operator's disk is sold
 * in. The figure counts live attachments only, so every reading of it is a
 * lower bound; the screen says so where it is shown rather than here.
 */
export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  const units = ['B', 'kB', 'MB', 'GB', 'TB']
  const exponent = Math.min(Math.floor(Math.log10(bytes) / 3), units.length - 1)
  const value = bytes / 1000 ** exponent
  const unit = units[exponent] ?? 'B'
  return `${exponent === 0 ? value : value.toFixed(value < 10 ? 1 : 0)} ${unit}`
}
