import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * Interface preferences that belong to the browser rather than the account:
 * the theme and whether the navigation rail is collapsed.
 *
 * Persisted to `localStorage` because losing them on reload is annoying and
 * neither is sensitive. Nothing about the session is kept here.
 */

export type Theme = 'light' | 'dark' | 'system'

interface UiState {
  theme: Theme
  sidebarCollapsed: boolean
}

interface UiActions {
  setTheme: (theme: Theme) => void
  toggleSidebar: () => void
  setSidebarCollapsed: (collapsed: boolean) => void
}

export const useUiStore = create<UiState & UiActions>()(
  persist(
    (set) => ({
      theme: 'system',
      sidebarCollapsed: false,

      setTheme: (theme) => set({ theme }),
      toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
      setSidebarCollapsed: (sidebarCollapsed) => set({ sidebarCollapsed }),
    }),
    { name: 'tmp.ui' },
  ),
)

/** Resolves `system` against the OS preference. */
export function resolveTheme(theme: Theme): 'light' | 'dark' {
  if (theme !== 'system') return theme
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}
