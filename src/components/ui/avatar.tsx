"use client";

import { cn } from "@/lib/utils";
import { Avatar as BaseAvatar } from "@base-ui/react/avatar";
import { forwardRef } from "react";

const Avatar = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof BaseAvatar.Root>
>(({ className, ...props }, ref) => (
  <BaseAvatar.Root
    ref={ref}
    className={cn(
      "relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full",
      className
    )}
    {...props}
  />
));
Avatar.displayName = "Avatar";

const AvatarImage = forwardRef<
  HTMLImageElement,
  React.ComponentPropsWithoutRef<typeof BaseAvatar.Image>
>(({ className, ...props }, ref) => (
  <BaseAvatar.Image
    ref={ref}
    className={cn("aspect-square h-full w-full", className)}
    {...props}
  />
));
AvatarImage.displayName = "AvatarImage";

const AvatarFallback = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof BaseAvatar.Fallback>
>(({ className, ...props }, ref) => (
  <BaseAvatar.Fallback
    ref={ref}
    className={cn(
      "flex h-full w-full items-center justify-center rounded-full bg-muted text-xs font-medium",
      className
    )}
    {...props}
  />
));
AvatarFallback.displayName = "AvatarFallback";

export { Avatar, AvatarImage, AvatarFallback };
