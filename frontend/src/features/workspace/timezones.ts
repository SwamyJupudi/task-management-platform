/**
 * The time zones the picker offers.
 *
 * Read from the browser rather than from the API, because there is no endpoint
 * that lists them: the backend validates a submitted zone against the JVM's own
 * database and answers 400 for anything it does not recognise. `Intl` is the
 * equivalent list on this side and needs no dependency.
 *
 * The two databases are the same IANA data and agree in practice, but they are
 * updated on their own schedules, so a zone added days ago could in principle
 * be offered here and refused there. The backend's refusal is the authority
 * and its message is shown as it arrives.
 *
 * `supportedValuesOf` is not in every engine's type definitions, so the call is
 * guarded and falls back to the workspace's current zone plus UTC. A picker
 * with two entries is worse than a long one and better than a crash.
 */
export function supportedTimezones(current: string): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] }

  const zones =
    typeof intl.supportedValuesOf === 'function' ? intl.supportedValuesOf('timeZone') : []

  // The current value is included whatever the browser thinks, so a workspace
  // already on a zone this engine does not list still shows what it is on.
  const all = new Set<string>(['UTC', current, ...zones])
  return [...all].filter((zone) => zone !== '').sort((a, b) => a.localeCompare(b))
}
