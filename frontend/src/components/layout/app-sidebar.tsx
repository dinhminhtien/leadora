"use client";

/**
 * Sidebar — Website UI Blueprint §4.1 and §5 (responsive).
 *
 * Widths: **256 expanded · 72 rail** · off-canvas below `md`. State persists per
 * user via `ui_store`.
 *
 * Anatomy top-to-bottom (§4.1): brand block · command trigger · grouped primary
 * nav · secondary (help/shortcuts) · workspace footer.
 *
 * Active-link rule is preserved from the existing implementation and is subtle
 * enough to be worth restating: the active item is the **longest matching
 * href**, so `/quotations/pending-approvals` lights up "Pending Approvals" only
 * — a plain `startsWith` would light up its parent "Quotations" too.
 *
 * Visibility is delegated to `canAccessPath`; this component never decides who
 * sees what.
 */

import * as React from "react";
import Link from "next/link";
import { ChevronsLeft, PanelLeft, Search, Sparkles } from "lucide-react";

import { cn } from "@/lib/utils";
import { NAV_GROUPS } from "@/app/routes/navigation";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { canAccessPath, type AppRole } from "@/shared/auth/access";
import { Kbd } from "@/components/layout/command-palette";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

type AppSidebarProps = {
  role: AppRole;
  permissions: string[];
  pathname: string;
  /** Role home — the generic "Dashboard" entry points here. */
  roleDashboard: string;
  expanded: boolean;
  onToggle: () => void;
  /** Mobile off-canvas visibility (< md). */
  mobileOpen: boolean;
  onMobileClose: () => void;
  onOpenPalette: () => void;
  unreadCount?: number;
  workspaceName?: string;
};

export function AppSidebar({
  role,
  permissions,
  pathname,
  roleDashboard,
  expanded,
  onToggle,
  mobileOpen,
  onMobileClose,
  onOpenPalette,
  unreadCount = 0,
  workspaceName = "Leadora",
}: AppSidebarProps) {
  // Resolve the generic Dashboard entry to this role's home before filtering, so
  // highlighting and navigation agree.
  const groups = React.useMemo(() => {
    const seen = new Set<string>();
    return NAV_GROUPS.map((group) => ({
      ...group,
      items: group.items
        .map((item) => ({
          ...item,
          href: item.href === ROUTE_PATHS.dashboard ? roleDashboard : item.href,
        }))
        .filter((item) => canAccessPath(role, item.href, permissions))
        // De-duplicate links that resolve to the same route (FO's "Dashboard"
        // and "Front Office Desk" are the same page).
        .filter((item) => {
          if (seen.has(item.href)) return false;
          seen.add(item.href);
          return true;
        }),
    })).filter((group) => group.items.length > 0);
  }, [role, permissions, roleDashboard]);

  // Longest-match wins — see the file header.
  const activeHref = React.useMemo(() => {
    let best: string | null = null;
    for (const group of groups) {
      for (const item of group.items) {
        const matches =
          pathname === item.href || pathname.startsWith(item.href + "/");
        if (matches && (best === null || item.href.length > best.length)) {
          best = item.href;
        }
      }
    }
    return best;
  }, [groups, pathname]);

  /**
   * Rendered twice — once for the desktop rail/column and once for the mobile
   * off-canvas drawer. The drawer is always 256 wide, so it always renders the
   * expanded layout regardless of the desktop collapse state.
   */
  const renderContent = (expanded: boolean) => (
    <TooltipProvider delayDuration={200}>
      <div className="flex h-full flex-col bg-sidebar">
        {/* ── Brand block (56 tall per §4.1) ─────────────────────────────── */}
        <div
          className={cn(
            "flex h-16 shrink-0 items-center border-b border-sidebar-border",
            expanded ? "gap-2.5 px-4" : "justify-center px-2",
          )}
        >
          <Link
            href={roleDashboard}
            className="flex min-w-0 items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            aria-label={`${workspaceName} home`}
          >
            {/* Existing brand mark is preserved — the blueprint changes chrome,
                not identity. */}
            <img
              src="/logo1.jpg"
              alt=""
              aria-hidden
              className="size-9 shrink-0 rounded-lg object-cover mix-blend-multiply dark:mix-blend-normal dark:invert"
            />
            {expanded && (
              <span className="flex min-w-0 flex-col leading-tight">
                <span className="truncate text-[14px] font-semibold tracking-tight text-foreground">
                  {workspaceName}
                </span>
                <span className="truncate text-[10.5px] text-muted-foreground">
                  Hospitality CRM
                </span>
              </span>
            )}
          </Link>

          {expanded && (
            <button
              type="button"
              onClick={onToggle}
              title="Collapse sidebar"
              aria-label="Collapse sidebar"
              className="ml-auto grid size-7 shrink-0 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              <ChevronsLeft className="size-4" />
            </button>
          )}
        </div>

        {/* ── Command trigger (§4.1.2) ───────────────────────────────────── */}
        <div className={cn("shrink-0", expanded ? "px-3 pt-3" : "px-2 pt-3")}>
          {expanded ? (
            <button
              type="button"
              onClick={onOpenPalette}
              className={cn(
                "flex h-9 w-full items-center gap-2 rounded-md border border-border bg-surface px-2.5",
                "text-[12.5px] text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              )}
            >
              <Search className="size-4 shrink-0" />
              <span className="flex-1 text-left">Search or jump…</span>
              <Kbd>⌘K</Kbd>
            </button>
          ) : (
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={onOpenPalette}
                  aria-label="Search or jump to"
                  className="grid h-9 w-full place-items-center rounded-md border border-border bg-surface text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
                >
                  <Search className="size-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">Search — ⌘K</TooltipContent>
            </Tooltip>
          )}
        </div>

        {/* ── Primary nav ────────────────────────────────────────────────── */}
        <nav
          aria-label="Main"
          className={cn(
            "min-h-0 flex-1 overflow-y-auto py-3",
            expanded ? "px-3" : "px-2",
          )}
        >
          {groups.map((group, gi) => (
            <div key={group.title} className={cn(gi > 0 && "mt-4")}>
              {expanded ? (
                <p className="px-2.5 pb-1.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                  {group.title}
                </p>
              ) : (
                gi > 0 && <div className="mx-2 mb-3 h-px bg-sidebar-border" />
              )}

              <ul className="space-y-0.5">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const active = item.href === activeHref;
                  const badge =
                    item.badge === "notifications" && unreadCount > 0
                      ? unreadCount > 99
                        ? "99+"
                        : String(unreadCount)
                      : null;

                  const link = (
                    <Link
                      href={item.href}
                      aria-current={active ? "page" : undefined}
                      title={!expanded ? item.label : undefined}
                      onClick={onMobileClose}
                      className={cn(
                        "group relative flex items-center rounded-md text-[13px] font-medium transition-colors",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                        expanded ? "gap-2.5 px-2.5 py-2" : "justify-center px-0 py-2.5",
                        active
                          ? "bg-brand-500/10 text-brand-600 dark:text-brand-500"
                          : "text-sidebar-foreground hover:bg-surface-2 hover:text-foreground",
                      )}
                    >
                      {/* 2px left rail marks the active item (§4.1). */}
                      {active && (
                        <span
                          aria-hidden
                          className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-r-full bg-brand-500"
                        />
                      )}
                      <Icon
                        className={cn(
                          "size-[18px] shrink-0",
                          active ? "text-brand-500" : "text-muted-foreground",
                        )}
                        strokeWidth={active ? 2.4 : 2}
                      />
                      {expanded && <span className="flex-1 truncate">{item.label}</span>}

                      {badge &&
                        (expanded ? (
                          <span className="numeric ml-auto rounded-pill bg-danger px-1.5 py-0.5 text-[10px] font-bold leading-none text-white">
                            {badge}
                          </span>
                        ) : (
                          // Rail mode shows a dot only, per §4.1.
                          <span
                            aria-hidden
                            className="absolute right-2 top-2 size-1.5 rounded-full bg-danger"
                          />
                        ))}
                    </Link>
                  );

                  return (
                    <li key={item.href}>
                      {expanded ? (
                        link
                      ) : (
                        <Tooltip>
                          <TooltipTrigger asChild>{link}</TooltipTrigger>
                          <TooltipContent side="right">
                            {item.label}
                            {badge && ` · ${badge} unread`}
                          </TooltipContent>
                        </Tooltip>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        {/* ── Workspace footer (§4.1.5) ──────────────────────────────────── */}
        <div
          className={cn(
            "shrink-0 border-t border-sidebar-border",
            expanded ? "p-3" : "p-2",
          )}
        >
          {expanded ? (
            <div className="flex items-center gap-2 rounded-md bg-surface-2 px-2.5 py-2">
              <span className="grid size-7 shrink-0 place-items-center rounded-md gradient-brand text-white">
                <Sparkles className="size-3.5" />
              </span>
              <span className="min-w-0 flex-1 leading-tight">
                <span className="block truncate text-[11.5px] font-semibold text-foreground">
                  Secure workspace
                </span>
                <span className="block truncate text-[10px] text-muted-foreground">
                  v1.0 · {role.toLowerCase()}
                </span>
              </span>
            </div>
          ) : (
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={onToggle}
                  aria-label="Expand sidebar"
                  className="grid h-9 w-full place-items-center rounded-md text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
                >
                  <PanelLeft className="size-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">Expand sidebar</TooltipContent>
            </Tooltip>
          )}
        </div>
      </div>
    </TooltipProvider>
  );

  return (
    <>
      {/* Desktop / tablet: in-flow rail or expanded column. */}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-30 hidden shrink-0 border-r border-sidebar-border md:block",
          "transition-[width] duration-[240ms] ease-[cubic-bezier(0.2,0,0,1)]",
          expanded ? "w-64" : "w-[72px]",
        )}
      >
        {renderContent(expanded)}
      </aside>

      {/* Mobile: off-canvas drawer below md (§5). */}
      <div
        className={cn(
          "fixed inset-0 z-50 md:hidden",
          mobileOpen ? "pointer-events-auto" : "pointer-events-none",
        )}
        aria-hidden={!mobileOpen}
      >
        <div
          onClick={onMobileClose}
          className={cn(
            "absolute inset-0 bg-foreground/40 transition-opacity duration-[240ms]",
            mobileOpen ? "opacity-100" : "opacity-0",
          )}
        />
        <div
          role="dialog"
          aria-modal={mobileOpen || undefined}
          aria-label="Navigation"
          className={cn(
            "absolute inset-y-0 left-0 w-64 border-r border-sidebar-border shadow-elev-4",
            "transition-transform duration-[240ms] ease-[cubic-bezier(0.2,0,0,1)]",
            mobileOpen ? "translate-x-0" : "-translate-x-full",
          )}
        >
          {renderContent(true)}
        </div>
      </div>
    </>
  );
}
