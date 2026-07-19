"use client";

import { cn } from "@/lib/utils";
import { Drawer as BaseDrawer } from "@base-ui/react/drawer";
import { X } from "lucide-react";
import { forwardRef } from "react";

const Sheet = BaseDrawer.Root;
const SheetTrigger = BaseDrawer.Trigger;
const SheetClose = BaseDrawer.Close;
const SheetPortal = BaseDrawer.Portal;

const SheetOverlay = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof BaseDrawer.Backdrop>
>(({ className, ...props }, ref) => (
  <BaseDrawer.Backdrop
    ref={ref}
    className={cn(
      "fixed inset-0 z-50 bg-black/50 backdrop-blur-sm data-[ending-style]:opacity-0 data-[starting-style]:opacity-0",
      className
    )}
    {...props}
  />
));
SheetOverlay.displayName = "SheetOverlay";

const SheetContent = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof BaseDrawer.Popup> & { side?: "left" | "right" }
>(({ className, children, side = "right", ...props }, ref) => {
  return (
    <BaseDrawer.Portal>
      <BaseDrawer.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
      <BaseDrawer.Popup
        ref={ref}
        className={cn(
          "fixed z-50 gap-4 bg-background p-6 shadow-lg transition-transform duration-200",
          side === "right" && "inset-y-0 right-0 h-full w-3/4 max-w-sm border-l data-[ending-style]:translate-x-0 data-[starting-style]:translate-x-full",
          side === "left" && "inset-y-0 left-0 h-full w-3/4 max-w-sm border-r data-[ending-style]:translate-x-0 data-[starting-style]:-translate-x-full",
          className
        )}
        data-side={side}
        {...props}
      >
        {children}
        <BaseDrawer.Close className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2">
          <X className="h-4 w-4" />
          <span className="sr-only">Close</span>
        </BaseDrawer.Close>
      </BaseDrawer.Popup>
    </BaseDrawer.Portal>
  );
});
SheetContent.displayName = "SheetContent";

const SheetHeader = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("flex flex-col space-y-2 text-center sm:text-left", className)} {...props} />
);
SheetHeader.displayName = "SheetHeader";

const SheetTitle = forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn("text-lg font-semibold text-foreground", className)} {...props} />
  )
);
SheetTitle.displayName = "SheetTitle";

export {
  Sheet,
  SheetTrigger,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetOverlay,
  SheetPortal,
};
