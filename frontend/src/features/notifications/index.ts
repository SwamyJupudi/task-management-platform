/**
 * The notifications feature's public surface.
 *
 * The shell needs the header entry and the count behind it. The feed, the
 * read transitions and the history are the notifications phase and will be
 * exported from here when they exist.
 */

export { NotificationsButton } from './components/notifications-button'
export { useUnreadNotificationCount } from './hooks'
