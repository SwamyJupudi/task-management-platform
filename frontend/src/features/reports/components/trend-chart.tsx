import { useMemo } from 'react'
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'

import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'

import type { Granularity, TrendPoint } from '../types'

/**
 * What arrived and what was finished, over the window asked about.
 *
 * Two lines rather than one, because either alone invites the wrong conclusion
 * confidently: finishing forty tasks a week is keeping up or falling behind
 * entirely depending on how many arrived. Reading them together is the point of
 * the chart, so they share an axis rather than sitting in two panels.
 *
 * Lines rather than bars. The backend returns every bucket in the window
 * including the empty ones, so the series is continuous and a line says
 * "measured over time" where a bar chart says "these are separate things".
 *
 * The tick labels are formatted from the granularity the data was asked for,
 * not guessed from the dates: a monthly chart reads "Mar 2026" and a daily one
 * "12 Mar", and inferring which from the gap between two points would get it
 * wrong on a window with one bucket in it.
 */

const CONFIG: ChartConfig = {
  created: { label: 'Created', color: 'var(--chart-1)' },
  completed: { label: 'Completed', color: 'var(--chart-3)' },
}

/** A bucket start as a tick, in the reader's own locale. */
function formatBucket(iso: string, granularity: Granularity | undefined): string {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) return iso

  if (granularity === 'MONTH') {
    return date.toLocaleDateString(undefined, { month: 'short', year: 'numeric' })
  }
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

export function TrendChart({
  points,
  granularity,
  className,
}: {
  points: TrendPoint[]
  /**
   * What the buckets are, when the request named it. Undefined means the
   * backend chose — days for a short window, weeks for a long one — and the
   * ticks fall back to the day-and-month form, which reads correctly for both.
   */
  granularity?: Granularity | undefined
  className?: string
}) {
  const rows = useMemo(
    () => points.map((point) => ({ ...point, tick: formatBucket(point.bucketStart, granularity) })),
    [points, granularity],
  )

  const totals = useMemo(
    () =>
      points.reduce(
        (sum, point) => ({
          created: sum.created + point.created,
          completed: sum.completed + point.completed,
        }),
        { created: 0, completed: 0 },
      ),
    [points],
  )

  return (
    <>
      {/* The chart itself is hidden from assistive technology and the same
          facts are given as a sentence, the way every other chart here does
          it: a reader who cannot see the lines still gets the comparison. */}
      <span className="sr-only">
        Over {rows.length.toLocaleString()} periods, {totals.created.toLocaleString()} tasks were
        created and {totals.completed.toLocaleString()} were completed.
      </span>

      <ChartContainer
        config={CONFIG}
        className={className ?? 'h-[240px] w-full'}
        aria-hidden="true"
      >
        <LineChart accessibilityLayer data={rows} margin={{ left: 0, right: 12, top: 8 }}>
          <CartesianGrid vertical={false} />
          <XAxis
            dataKey="tick"
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            tick={{ fontSize: 12 }}
            // A long window has more buckets than fit; Recharts drops the ones
            // that would collide rather than overlapping the labels.
            interval="preserveStartEnd"
            minTickGap={24}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            width={32}
            allowDecimals={false}
            tick={{ fontSize: 12 }}
            // Half a task is not a thing, and an empty window should read 0 to
            // 1 rather than collapsing the axis onto itself.
            domain={[0, totals.created + totals.completed === 0 ? 1 : 'auto']}
          />
          <ChartTooltip content={<ChartTooltipContent />} />
          <ChartLegend content={<ChartLegendContent />} />
          <Line
            dataKey="created"
            type="monotone"
            stroke="var(--color-created)"
            strokeWidth={2}
            dot={false}
          />
          <Line
            dataKey="completed"
            type="monotone"
            stroke="var(--color-completed)"
            strokeWidth={2}
            dot={false}
          />
        </LineChart>
      </ChartContainer>
    </>
  )
}
