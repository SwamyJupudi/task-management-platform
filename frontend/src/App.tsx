import { AppProviders } from '@/app/providers/app-providers'
import { AppRouter } from '@/app/routes/app-router'
import { ErrorBoundary } from '@/components/common/error-boundary'

export default function App() {
  return (
    <ErrorBoundary>
      <AppProviders>
        <AppRouter />
      </AppProviders>
    </ErrorBoundary>
  )
}
