"use client";

/**
 * Notification centre popover — Website UI Blueprint §3.7.
 *
 * 400×560 popover, tabs `All / Unread`, grouped by day, unread rows tinted with
 * a brand dot. The deep-link map is preserved **exactly** from the existing
 * implementation (spec §7.1) — it is business behaviour, not styling.
 *
 * Two behaviours worth keeping visible because they are easy to break:
 * - Clicking a row **always** marks it read, but only navigates when the row
 *   resolves to a route. Rows with no destination stay put so the user can see
 *   the read styling change in place.
 * - Navigation must not wait on the mark-read call: it is a best-effort side
 *   effect, not a gate.
 */

import * as React from "react";
import { useRouter } from "next/navigation";
import { Bell, BellOff, CheckCheck, Settings2 } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { ListSkeleton } from "@/components/ui/skeletons";
import { EmptyState } from "@/components/ui/states";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import type { Notification } from "@/services/notification_service";

/**
 * Entity → route. Unchanged from the existing shell, including the note that
 * no standalone quotation detail page exists, so QUOTATION rows deep-link to
 * the list with a highlight param.
 */
export function notificationRoute(n: Notification): string | null {
  if (!n.relatedEntity || !n.relatedId) return null;
  const entity = n.relatedEntity.toUpperCase();
  const highlight = `highlight=${encodeURIComponent(n.relatedId)}`;
  if (entity === "LEAD") return ROUTE_PATHS.leadDetail(n.relatedId);
  if (entity === "QUOTATION") return `${ROUTE_PATHS.quotations}?${highlight}`;
  if (entity === "BOOKING") return `${ROUTE_PATHS.bookingConfirmation}?${highlight}`;
  if (entity === "REMINDER") return `${ROUTE_PATHS.reminders}?${highlight}`;
  if (entity === "TASK") return `${ROUTE_PATHS.followUpTasks}?${highlight}`;
  if (entity === "SLA") return `${ROUTE_PATHS.sla}?${highlight}`;
  if (entity === "HANDOVER") return `${ROUTE_PATHS.frontOfficeHandover}?${highlight}`;
  return null;
}

/** Relative time, unchanged in behaviour from the previous shell. */
function formatNotifTime(iso: string): string {
  const diffMin = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (diffMin < 1) return "Just now";
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}h ago`;
  return `${Math.floor(diffH / 24)}d ago`;
}

/** §3.7 groups rows by day. */
function dayBucket(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  if (sameDay(d, today)) return "Today";
  if (sameDay(d, yesterday)) return "Yesterday";
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

const priorityDot: Record<string, string> = {
  URGENT: "bg-danger",
  HIGH: "bg-warning",
  NORMAL: "bg-brand-500",
  LOW: "bg-muted-foreground",
};

type NotificationCenterProps = {
  notifications: Notification[];
  unreadCount: number;
  isLoading?: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onMarkRead: (id: string, read: boolean) => void;
  onMarkAllRead: () => void;
};

export function NotificationCenter({
  notifications,
  unreadCount,
  isLoading = false,
  open,
  onOpenChange,
  onMarkRead,
  onMarkAllRead,
}: NotificationCenterProps) {
  const router = useRouter();
  const [tab, setTab] = React.useState<"all" | "unread">("all");

  const rows = React.useMemo(
    () => (tab === "unread" ? notifications.filter((n) => !n.isRead) : notifications),
    [notifications, tab],
  );

  const grouped = React.useMemo(() => {
    const map = new Map<string, Notification[]>();
    for (const n of rows) {
      const key = dayBucket(n.createdAt);
      const list = map.get(key);
      if (list) list.push(n);
      else map.set(key, [n]);
    }
    return [...map.entries()];
  }, [rows]);

  const handleClick = (n: Notification) => {
    const route = notificationRoute(n);
    if (route) {
      onOpenChange(false);
      router.push(route);
    }
    // Fire-and-forget: navigation above must not depend on this resolving.
    if (!n.isRead) onMarkRead(n.id, true);
  };

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={
            unreadCount > 0
              ? `Notifications — ${unreadCount} unread`
              : "Notifications"
          }
          className={cn(
            "relative grid size-9 place-items-center rounded-md text-muted-foreground",
            "transition-colors hover:bg-surface-2 hover:text-foreground",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
          )}
        >
          <Bell className="size-[18px]" />
          {unreadCount > 0 && (
            <span className="numeric absolute -right-0.5 -top-0.5 grid h-4 min-w-4 place-items-center rounded-pill bg-danger px-1 text-[9.5px] font-bold leading-none text-white ring-2 ring-background">
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          )}
        </button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-[400px] p-0" sideOffset={8}>
        <div className="flex items-center justify-between border-b border-border px-3 py-2.5">
          <div className="flex items-center gap-2">
            <p className="text-[13px] font-semibold text-foreground">Notifications</p>
            {unreadCount > 0 && (
              <span className="numeric rounded-pill bg-brand-500/10 px-1.5 py-0.5 text-[10.5px] font-semibold text-brand-600 dark:text-brand-500">
                {unreadCount} unread
              </span>
            )}
          </div>
          {unreadCount > 0 && (
            <Button
              size="xs"
              variant="ghost"
              onClick={onMarkAllRead}
              leftIcon={<CheckCheck className="size-3.5" />}
            >
              Mark all read
            </Button>
          )}
        </div>

        {/* Tabs — §3.7. "Mentions" is omitted: the system has no mention feature. */}
        <div
          role="tablist"
          aria-label="Notification filter"
          className="flex items-center gap-1 border-b border-border px-2 py-1.5"
        >
          {(["all", "unread"] as const).map((key) => (
            <button
              key={key}
              role="tab"
              aria-selected={tab === key}
              onClick={() => setTab(key)}
              className={cn(
                "rounded-md px-2.5 py-1 text-[12px] font-medium capitalize transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                tab === key
                  ? "bg-brand-500/10 text-brand-600 dark:text-brand-500"
                  : "text-muted-foreground hover:bg-surface-2 hover:text-foreground",
              )}
            >
              {key}
            </button>
          ))}
        </div>

        <div className="max-h-[420px] overflow-y-auto">
          {isLoading ? (
            <div className="p-3">
              <ListSkeleton count={3} />
            </div>
          ) : rows.length === 0 ? (
            <EmptyState
              dense
              icon={BellOff}
              title={tab === "unread" ? "No unread alerts" : "You're all caught up"}
              message={
                tab === "unread"
                  ? "Everything here has been read."
                  : "New alerts will appear here."
              }
            />
          ) : (
            grouped.map(([day, items]) => (
              <div key={day}>
                <p className="sticky top-0 z-10 bg-popover/95 px-3 py-1.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground backdrop-blur">
                  {day}
                </p>
                {items.map((n) => (
                  <button
                    key={n.id}
                    onClick={() => handleClick(n)}
                    className={cn(
                      "flex w-full items-start gap-2.5 border-b border-border/60 px-3 py-2.5 text-left transition-colors last:border-b-0",
                      "hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500",
                      !n.isRead && "bg-brand-500/[0.06]",
                    )}
                  >
                    <span
                      aria-hidden
                      className={cn(
                        "mt-1.5 size-1.5 shrink-0 rounded-full",
                        n.isRead
                          ? "bg-transparent ring-1 ring-border"
                          : (priorityDot[n.priority ?? "NORMAL"] ?? "bg-brand-500"),
                      )}
                    />
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          "block truncate text-[12.5px]",
                          n.isRead
                            ? "font-medium text-muted-foreground"
                            : "font-semibold text-foreground",
                        )}
                      >
                        {n.title}
                      </span>
                      <span className="mt-0.5 line-clamp-2 block text-[11.5px] leading-[16px] text-muted-foreground">
                        {n.message}
                      </span>
                      <span className="mt-1 flex items-center gap-2">
                        {n.relatedEntity && (
                          <span className="rounded border border-border bg-muted px-1 py-px text-[9.5px] font-semibold uppercase tracking-wide text-muted-foreground">
                            {n.relatedEntity}
                          </span>
                        )}
                        <span className="text-[10.5px] text-muted-foreground/80">
                          {formatNotifTime(n.createdAt)}
                        </span>
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            ))
          )}
        </div>

        <div className="flex items-center justify-between border-t border-border px-3 py-2">
          <Button
            size="xs"
            variant="link"
            onClick={() => {
              onOpenChange(false);
              router.push(ROUTE_PATHS.notifications);
            }}
          >
            View all
          </Button>
          <Button
            size="xs"
            variant="ghost"
            onClick={() => {
              onOpenChange(false);
              router.push(ROUTE_PATHS.profile);
            }}
            leftIcon={<Settings2 className="size-3.5" />}
          >
            Preferences
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
