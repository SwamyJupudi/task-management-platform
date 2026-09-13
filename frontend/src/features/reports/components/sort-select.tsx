import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/**
 * The sort, restricted to what the endpoint's allowlist holds.
 *
 * Each report has its own short list, copied from `ReportSorts`, and only those
 * values are offered: an unknown field is answered 400 rather than ignored, so
 * offering a column the module cannot sort by would produce a broken screen
 * rather than an unsorted one.
 */
export function SortSelect({
  value,
  options,
  onChange,
  id,
}: {
  value: string
  options: readonly { value: string; label: string }[]
  onChange: (next: string) => void
  /** Distinct per screen, so the label points at the right control. */
  id: string
}) {
  return (
    <div className="flex items-center gap-2">
      <Label htmlFor={id} className="shrink-0 text-xs text-muted-foreground">
        Sort
      </Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id={id} className="h-8 w-[12rem]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
