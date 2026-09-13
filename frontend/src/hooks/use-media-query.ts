import { useCallback, useSyncExternalStore } from 'react'

/**
 * Whether a CSS media query currently matches, as React state.
 *
 * For the handful of decisions that cannot be made in CSS because they change
 * what is mounted rather than how it looks. Everything that is only a matter
 * of appearance stays in Tailwind's responsive variants, where it costs no
 * render.
 *
 * `useSyncExternalStore` rather than an effect: `matchMedia` is an external
 * store, the value is read during the render that needs it, and there is no
 * frame in which the component has rendered against a stale answer.
 *
 * The server snapshot is false. Nothing renders on a server here, but the same
 * value is what a test environment without `matchMedia` sees, and "not
 * matching" is the safe default for every caller.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onStoreChange: () => void) => {
      const media = window.matchMedia(query)
      media.addEventListener('change', onStoreChange)
      return () => media.removeEventListener('change', onStoreChange)
    },
    [query],
  )

  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => false,
  )
}
