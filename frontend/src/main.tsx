import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './App'
import { configureApiClient } from './lib/api'
import { env } from './config/env'
import { getAccessToken } from './stores/session-store'
import './index.css'

/**
 * Gives the API client its token source.
 *
 * `refreshToken` and `onSessionExpired` are deliberately left unset: they
 * belong to the authentication feature, and until it exists a 401 simply
 * surfaces as an ApiError rather than triggering a refresh.
 */
configureApiClient({ getToken: getAccessToken })

document.title = env.appName

const container = document.getElementById('root')
if (!container) throw new Error('The #root element is missing from index.html.')

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
