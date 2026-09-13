import { useEffect } from 'react'

import { resolveTheme, useUiStore } from '@/stores/ui-store'

/**
 * Keeps the `dark` class on `<html>` in step with the stored preference,
 * which is the switch every token in index.css hangs off.
 *
 * Also follows the OS while the preference is `system`, so the interface
 * changes with the machine rather than only on reload.
 */
export function useTheme(): void {
  const theme = useUiStore((state) => state.theme)

  useEffect(() => {
    const root = document.documentElement

    const apply = () => {
      const resolved = resolveTheme(theme)
      root.classList.toggle('dark', resolved === 'dark')
      root.style.colorScheme = resolved
    }

    apply()

    if (theme !== 'system') return
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [theme])
}
