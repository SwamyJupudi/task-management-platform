import { Component, type ErrorInfo, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'

/**
 * The last line of defence for a render-time crash.
 *
 * A thrown render error would otherwise unmount the whole tree and leave a
 * blank page. This catches it, shows a generic message, and keeps the detail
 * in the console where a developer can reach it and a user cannot.
 */

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
}

export class ErrorBoundary extends Component<Props, State> {
  override state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    // Deliberately console-only. Wiring this to a reporting service belongs
    // with the rest of observability, not with the scaffold.
    console.error('Unhandled render error', error, info.componentStack)
  }

  override render(): ReactNode {
    if (!this.state.hasError) return this.props.children
    if (this.props.fallback) return this.props.fallback

    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-4 px-6 text-center">
        <div className="space-y-1">
          <h1 className="text-lg font-semibold">Something went wrong</h1>
          <p className="text-muted-foreground max-w-sm text-sm">
            The page could not be displayed. Reloading usually clears it.
          </p>
        </div>
        <Button onClick={() => window.location.reload()}>Reload the page</Button>
      </div>
    )
  }
}
