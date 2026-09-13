import { ConstructionIcon } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'

/**
 * Stands in for a screen whose feature has not been built.
 *
 * Every route in the router resolves to something, so navigation and the
 * guards can be exercised before a single feature exists. These are replaced
 * one at a time as the feature folders fill in.
 */
export function PlaceholderPage({ title, description }: { title: string; description?: string }) {
  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold">{title}</h1>
      <EmptyState
        icon={ConstructionIcon}
        title="Not built yet"
        description={description ?? 'This screen is part of a later phase.'}
      />
    </div>
  )
}
