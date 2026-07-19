import { cn } from "@/lib/utils";
import { Separator as BaseSeparator } from "@base-ui/react/separator";
import { forwardRef } from "react";

const Separator = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof BaseSeparator>
>(({ className, orientation = "horizontal", ...props }, ref) => (
  <BaseSeparator
    ref={ref}
    orientation={orientation}
    className={cn(
      "shrink-0 bg-border",
      orientation === "horizontal" ? "h-px w-full" : "h-full w-px",
      className
    )}
    {...props}
  />
));
Separator.displayName = "Separator";

export { Separator };
