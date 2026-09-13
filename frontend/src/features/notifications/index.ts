/**
 * The notifications feature's public surface.
 *
 * The shell needs the header entry and the count behind it; the router needs
 * the feed. Everything else — the API calls, the read transitions and the rule
 * that decides where a notification leads — is internal.
 */

export { NotificationsButton } from './components/notifications-button'
export { NotificationsPage } from './pages/notifications-page'
export { useUnreadNotificationCount } from './hooks'
