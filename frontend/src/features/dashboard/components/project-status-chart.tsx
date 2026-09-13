import { useMemo } from 'react'
import { Cell, Label, Pie, PieChart } from 'recharts'

import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'

import type { ProjectStatusCount } from '../types'

/**
 * Projects by lifecycle state, as a ring with the total in the middle.
 *
 * A ring rather than bars because this breakdown is genuinely a composition —
 * every project is in exactly one state and the parts add to the whole — and
 * because the hole is somewhere honest to put the total, which is the figure
 * people actually want from this panel.
 *
 * States holding nothing are dropped from the ring but kept in the legend
 * beside it, so the chart is not a row of invisible slices while the reader can
 * still see that "On hold" exists and is empty.
 */
export function ProjectStatusChart({ data }: { data: ProjectStatusCount[] }) {
  const config = useMemo<ChartConfig>(
    () =>
      Object.fromEntries(
        data.map((entry, index) => [
          entry.status,
          { label: entry.label, color: `var(--chart-${(index % 5) + 1})` },
        ]),
      ),
    [data],
  )

  const total = data.reduce((sum, entry) => sum + entry.count, 0)
  const slices = data.filter((entry) => entry.count > 0)

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row">
      <span className="sr-only">
        Projects by status, {total} in total:{' '}
        {data.map((entry) => `${entry.label}, ${entry.count}`).join('; ')}.
      </span>

      <ChartContainer
        config={config}
        className="aspect-square h-[180px] shrink-0"
        aria-hidden="true"
      >
        <PieChart>
          <ChartTooltip content={<ChartTooltipContent nameKey="status" hideLabel />} />
          <Pie data={slices} dataKey="count" nameKey="status" innerRadius={52} strokeWidth={2}>
            {slices.map((entry) => (
              <Cell key={entry.status} fill={`var(--color-${entry.status})`} />
            ))}
            <Label
              content={({ viewBox }) =>
                viewBox && 'cx' in viewBox ? (
                  <text x={viewBox.cx} y={viewBox.cy} textAnchor="middle">
                    <tspan
                      x={viewBox.cx}
                      y={viewBox.cy}
                      className="fill-foreground text-xl font-semibold"
                    >
                      {total.toLocaleString()}
                    </tspan>
                    <tspan
                      x={viewBox.cx}
                      y={(viewBox.cy ?? 0) + 18}
                      className="fill-muted-foreground text-xs"
                    >
                      {total === 1 ? 'project' : 'projects'}
                    </tspan>
                  </text>
                ) : null
              }
            />
          </Pie>
        </PieChart>
      </ChartContainer>

      <ul className="w-full min-w-0 space-y-1.5">
        {data.map((entry, index) => (
          <li key={entry.status} className="flex items-center gap-2 text-sm">
            <span
              className="size-2.5 shrink-0 rounded-[2px]"
              style={{ backgroundColor: `var(--chart-${(index % 5) + 1})` }}
              aria-hidden="true"
            />
            <span className="min-w-0 flex-1 truncate text-muted-foreground">{entry.label}</span>
            <span className="font-medium tabular-nums">{entry.count.toLocaleString()}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
