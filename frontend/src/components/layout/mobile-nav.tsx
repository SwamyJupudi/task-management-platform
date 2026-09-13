import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { env } from '@/config/env'

import { SidebarNav } from './sidebar-nav'
import { WorkspaceSwitcher } from './workspace-switcher'

/**
 * The same navigation as the rail, as a drawer, for a viewport too narrow to
 * give a column to it permanently.
 *
 * A dialog rather than a hidden panel, which is what buys the behaviour a
 * drawer is expected to have and would otherwise have to be written by hand:
 * focus moves inside and is trapped there, Escape closes it, the page behind
 * is inert, and the rest of the document is hidden from assistive technology
 * while it is open.
 */
export function MobileNav({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="left" className="w-72 gap-0 p-0">
        <SheetHeader className="px-4 pt-4 pb-2">
          <SheetTitle className="text-sm">{env.appName}</SheetTitle>
          <SheetDescription className="sr-only">
            Navigate between the screens in your workspace.
          </SheetDescription>
        </SheetHeader>

        <div className="px-2 pb-2">
          <WorkspaceSwitcher />
        </div>

        <SidebarNav onNavigate={() => onOpenChange(false)} />
      </SheetContent>
    </Sheet>
  )
}
