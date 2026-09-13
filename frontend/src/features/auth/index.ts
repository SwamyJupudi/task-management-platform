/**
 * The authentication feature's public surface.
 *
 * Everything outside this folder imports from here and from nowhere deeper, so
 * the feature's internals stay free to move. The application shell needs the
 * gate, the sign-out hook and the six screens; the entry point needs the
 * client wiring. Nothing else is exported.
 */

export { SessionGate } from './session-gate'
export { installSessionManager } from './session-manager'
export { useLogout } from './hooks'

export { LoginPage } from './pages/login-page'
export { RegisterPage } from './pages/register-page'
export { VerifyEmailPage } from './pages/verify-email-page'
export { ForgotPasswordPage } from './pages/forgot-password-page'
export { ResetPasswordPage } from './pages/reset-password-page'
export { AcceptInvitationPage } from './pages/accept-invitation-page'
