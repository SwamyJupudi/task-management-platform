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

/**
 * The three seams the account screens need.
 *
 * Anything that mints or destroys a token stays in this feature, so the account
 * screens call these rather than handling tokens themselves: changing a
 * password issues a new pair, signing out everywhere destroys them all, and
 * editing a profile changes what `/auth/me` would say next time.
 *
 * `FormError` travels with them because it is the application's one way of
 * showing a rejected request — the backend's own wording, plus the request id
 * support needs — and the account forms should not invent a second. So does
 * the password schema, which belongs beside the ones registration and reset
 * use so that what a password must be is defined once.
 */
export { useChangePassword, useLogoutEverywhere } from './hooks'
export { reloadCurrentUser } from './session-manager'
export { FormError } from './components/form-error'
export { changePasswordSchema, type ChangePasswordValues } from './schemas'

export { LoginPage } from './pages/login-page'
export { RegisterPage } from './pages/register-page'
export { VerifyEmailPage } from './pages/verify-email-page'
export { ForgotPasswordPage } from './pages/forgot-password-page'
export { ResetPasswordPage } from './pages/reset-password-page'
