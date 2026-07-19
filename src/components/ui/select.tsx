"use client";

import { cn } from "@/lib/utils";
import { Select as BaseSelect } from "@base-ui/react/select";
import { ChevronDown } from "lucide-react";
import { forwardRef } from "react";

interface SelectProps<Value = string> {
  children?: React.ReactNode;
  className?: string;
  value?: Value | null;
  defaultValue?: Value | null;
  onValueChange?: (value: Value, eventDetails: any) => void;
  disabled?: boolean;
  required?: boolean;
  name?: string;
}

function SelectInner<Value = string>(
  { className, children, onValueChange, value, defaultValue, disabled, required, name }: SelectProps<Value>,
  ref: React.ForwardedRef<HTMLButtonElement>
) {
  return (
    <BaseSelect.Root<Value, false>
      onValueChange={onValueChange as any}
      value={value as any}
      defaultValue={defaultValue as any}
      disabled={disabled}
      required={required}
      name={name}
    >
      <BaseSelect.Trigger
        ref={ref}
        className={cn(
          "flex h-10 w-full items-center justify-between rounded-lg border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 [&>span]:line-clamp-1",
          className
        )}
      >
        <BaseSelect.Value placeholder="Select..." />
        <BaseSelect.Icon>
          <ChevronDown className="h-4 w-4 opacity-50" />
        </BaseSelect.Icon>
      </BaseSelect.Trigger>
      <BaseSelect.Portal>
        <BaseSelect.Positioner sideOffset={4}>
          <BaseSelect.Popup className="z-50 min-w-[8rem] overflow-hidden rounded-lg border bg-popover p-1 text-popover-foreground shadow-md">
            {children}
          </BaseSelect.Popup>
        </BaseSelect.Positioner>
      </BaseSelect.Portal>
    </BaseSelect.Root>
  );
}

const Select = forwardRef(SelectInner) as unknown as (<Value = string>(
  props: SelectProps<Value> & { ref?: React.ForwardedRef<HTMLButtonElement> }
) => ReturnType<typeof SelectInner>) & { displayName: string };

Select.displayName = "Select";

const SelectItem = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof BaseSelect.Item>
>(({ className, children, ...props }, ref) => (
  <BaseSelect.Item
    ref={ref}
    className={cn(
      "relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
      className
    )}
    {...props}
  >
    <BaseSelect.ItemIndicator className="absolute right-2 flex h-3.5 w-3.5 items-center justify-center">
      <span className="h-2 w-2 rounded-full bg-primary" />
    </BaseSelect.ItemIndicator>
    <BaseSelect.ItemText>{children}</BaseSelect.ItemText>
  </BaseSelect.Item>
));
SelectItem.displayName = "SelectItem";

export { Select, SelectItem };
