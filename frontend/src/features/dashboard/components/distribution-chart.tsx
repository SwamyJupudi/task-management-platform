import { useMemo } from 'react'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'

import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'

import type { CountByKey } from '../types'

/**
 * A breakdown as a horizontal bar chart.
 *
 * Horizontal because the labels are words — "In progress", "Critical" — and a
 * vertical chart either truncates them or turns them on their side. Read down
 * the left edge, the categories stay legible at every width, which is what
 * makes the same component work on a phone and on a dashboard column.
 *
 * The colours come from the `--chart-1` to `--chart-5` tokens already defined
 * in `index.css`, so the chart follows the theme — including the dark one —
 * without knowing anything about it.
 *
 * Every column the backend sends is drawn, including the ones at zero. A status
 * nothing holds is a fact about the workspace, and dropping it would silently
 * change the shape of the chart from one week to the next.
 */
export function DistributionChart({
  data,
  ariaLabel,
}: {
  data: CountByKey[]
  /** What the chart is of, for a reader who cannot see it. */
  ariaLabel: string
}) {
  const config = useMemo<ChartConfig>(() => ({ count: { label: 'Tasks' } }), [])

  const rows = useMemo(
    () =>
      data.map((entry, index) => ({
        ...entry,
        // Cycles through the five chart tokens, so a breakdown with more
        // columns than tokens repeats rather than running out of colour.
        fill: `var(--chart-${(index % 5) + 1})`,
      })),
    [data],
  )

  const total = rows.reduce((sum, row) => sum + row.count, 0)

  return (
    <>
      <span className="sr-only">
        {ariaLabel}: {rows.map((row) => `${row.label}, ${row.count}`).join('; ')}.
      </span>

      <ChartContainer config={config} className="h-[200px] w-full" aria-hidden="true">
        <BarChart accessibilityLayer data={rows} layout="vertical" margin={{ left: 0, right: 12 }}>
          <CartesianGrid horizontal={false} />
          <YAxis
            dataKey="label"
            type="category"
            tickLine={false}
            axisLine={false}
            width={92}
            tick={{ fontSize: 12 }}
          />
          {/* A whole-number axis: half a task is not a thing, and an empty
              workspace should read 0 to 1 rather than 0 to 0. */}
          <XAxis type="number" allowDecimals={false} domain={[0, total === 0 ? 1 : 'auto']} hide />
          <ChartTooltip cursor={false} content={<ChartTooltipContent hideLabel />} />
          <Bar dataKey="count" radius={4} barSize={18} />
        </BarChart>
      </ChartContainer>
    </>
  )
}
