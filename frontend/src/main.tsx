import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './App'
import { installSessionManager } from './features/auth'
import { configureApiClient } from './lib/api'
import { env } from './config/env'
import { getAccessToken } from './stores/session-store'
import './index.css'

/**
 * Gives the API client its token source, then its two session seams.
 *
 * `installSessionManager` fills in `refreshToken` and `onSessionExpired`, so a
 * 401 on a call that carried a token is retried once behind a silent refresh
 * and only becomes a sign-out when the refresh cookie is gone too.
 *
 * Both run before the first render, so no request can be made against a client
 * that is only half configured.
 */
configureApiClient({ getToken: getAccessToken })
installSessionManager()

document.title = env.appName

const container = document.getElementById('root')
if (!container) throw new Error('The #root element is missing from index.html.')

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
