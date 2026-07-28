"use client";

/**
 * Side panel / Drawer — Website UI Blueprint §2.11.
 *
 * Widths `sm 360 · md 480 · lg 640`. Slides from the right over `--motion-3`
 * with a 40% backdrop. Header pinned · body scrolls · footer pinned.
 *
 * The blueprint's division of labour: **drawer for read-heavy detail**
 * (Task detail, Lead detail), **modal for transactional forms**. Built on the
 * same Radix Dialog primitive so focus trapping, scroll lock and `Esc` behave
 * identically in both.
 */

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";

import { cn } from "@/lib/utils";

const Drawer = DialogPrimitive.Root;
const DrawerTrigger = DialogPrimitive.Trigger;
const DrawerClose = DialogPrimitive.Close;
const DrawerPortal = DialogPrimitive.Portal;

export type DrawerSize = "sm" | "md" | "lg";

const sizeClasses: Record<DrawerSize, string> = {
  sm: "max-w-[360px]",
  md: "max-w-[480px]",
  lg: "max-w-[640px]",
};

type DrawerContentProps = React.ComponentPropsWithoutRef<
  typeof DialogPrimitive.Content
> & {
  size?: DrawerSize;
  side?: "right" | "left";
  dismissible?: boolean;
  showClose?: boolean;
};

const DrawerContent = React.forwardRef<
  React.ComponentRef<typeof DialogPrimitive.Content>,
  DrawerContentProps
>(
  (
    {
      className,
      children,
      size = "md",
      side = "right",
      dismissible = true,
      showClose = true,
      ...props
    },
    ref,
  ) => (
    <DrawerPortal>
      <DialogPrimitive.Overlay
        className={cn(
          "fixed inset-0 z-50 bg-foreground/40",
          "data-[state=open]:animate-in data-[state=open]:fade-in-0",
          "data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
        )}
      />
      <DialogPrimitive.Content
        ref={ref}
        onEscapeKeyDown={(e) => {
          if (!dismissible) e.preventDefault();
        }}
        onPointerDownOutside={(e) => {
          if (!dismissible) e.preventDefault();
        }}
        onInteractOutside={(e) => {
          if (!dismissible) e.preventDefault();
        }}
        className={cn(
          "fixed inset-y-0 z-50 flex w-full flex-col border-border bg-card text-card-foreground shadow-elev-4",
          "duration-[240ms] ease-[cubic-bezier(0.2,0,0,1)]",
          side === "right"
            ? "right-0 border-l data-[state=open]:animate-in data-[state=open]:slide-in-from-right data-[state=closed]:animate-out data-[state=closed]:slide-out-to-right"
            : "left-0 border-r data-[state=open]:animate-in data-[state=open]:slide-in-from-left data-[state=closed]:animate-out data-[state=closed]:slide-out-to-left",
          sizeClasses[size],
          className,
        )}
        {...props}
      >
        {children}
        {showClose && (
          <DialogPrimitive.Close
            aria-label="Close panel"
            className={cn(
              "absolute right-4 top-4 grid size-8 place-items-center rounded-md text-muted-foreground",
              "transition-colors hover:bg-muted hover:text-foreground",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-card",
            )}
          >
            <X className="size-4" />
          </DialogPrimitive.Close>
        )}
      </DialogPrimitive.Content>
    </DrawerPortal>
  ),
);
DrawerContent.displayName = "DrawerContent";

function DrawerHeader({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "flex shrink-0 flex-col gap-1 border-b border-border px-5 py-4 pr-14",
        className,
      )}
      {...props}
    />
  );
}

function DrawerBody({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("min-h-0 flex-1 overflow-y-auto px-5 py-5", className)}
      {...props}
    />
  );
}

function DrawerFooter({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-border px-5 py-4",
        className,
      )}
      {...props}
    />
  );
}

const DrawerTitle = React.forwardRef<
  React.ComponentRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title
    ref={ref}
    className={cn(
      "text-[20px] font-semibold leading-7 tracking-[-0.005em] text-foreground",
      className,
    )}
    {...props}
  />
));
DrawerTitle.displayName = "DrawerTitle";

const DrawerDescription = React.forwardRef<
  React.ComponentRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description
    ref={ref}
    className={cn("text-[13px] leading-[18px] text-muted-foreground", className)}
    {...props}
  />
));
DrawerDescription.displayName = "DrawerDescription";

export {
  Drawer,
  DrawerTrigger,
  DrawerClose,
  DrawerPortal,
  DrawerContent,
  DrawerHeader,
  DrawerBody,
  DrawerFooter,
  DrawerTitle,
  DrawerDescription,
};
