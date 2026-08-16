"use client";

/**
 * Topbar — Website UI Blueprint §4.2, §4.5, §4.7, §4.8.
 *
 * `h-16`, sticky, `backdrop-blur`. Left: mobile menu toggle · workspace eyebrow.
 * Centre: command-palette trigger. Right: Quick Add · theme · help ·
 * notifications · avatar menu.
 *
 * **FO exception (§4.2).** The Front Office role gets a deliberately narrower
 * desk: no command trigger and no Quick Add. That mirrors the existing shell,
 * which hid search and Quick Add for FO, and the spec's BR-27 reasoning that FO
 * only ever acts on arrival readiness.
 */

import * as React from "react";
import { useRouter } from "next/navigation";
import {
  ChevronDown,
  Download,
  HelpCircle,
  Keyboard,
  LogOut,
  Menu,
  Moon,
  Plus,
  Search,
  Sun,
  User as UserIcon,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { Kbd } from "@/components/layout/command-palette";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { StatusPill } from "@/components/ui/status-pill";
import { QUICK_ADD_ITEMS } from "@/app/routes/navigation";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { canAccessPath, type AppRole } from "@/shared/auth/access";
import { useUiStore } from "@/stores/ui_store";
import type { User } from "@/shared/types/auth";

const ROLE_LABEL: Record<AppRole, string> = {
  ADMIN: "Admin",
  MANAGER: "Sales Manager",
  SALES: "Sales Staff",
  FO: "Front Office",
  RESERVATION: "Reservation",
};

type AppTopbarProps = {
  user: User | null;
  role: AppRole;
  permissions: string[];
  avatarSource: string | null;
  onOpenMobileNav: () => void;
  onOpenPalette: () => void;
  onOpenShortcuts: () => void;
  onLogout: () => void;
  /** Rendered between the eyebrow and the palette trigger. */
  notificationSlot?: React.ReactNode;
};

export function AppTopbar({
  user,
  role,
  permissions,
  avatarSource,
  onOpenMobileNav,
  onOpenPalette,
  onOpenShortcuts,
  onLogout,
  notificationSlot,
}: AppTopbarProps) {
  const router = useRouter();
  const { theme, toggleTheme } = useUiStore();
  const [quickAddOpen, setQuickAddOpen] = React.useState(false);
  const [userMenuOpen, setUserMenuOpen] = React.useState(false);
  const [helpOpen, setHelpOpen] = React.useState(false);

  const isFrontOffice = role === "FO";

  const quickAdd = React.useMemo(
    () => QUICK_ADD_ITEMS.filter((i) => canAccessPath(role, i.requires, permissions)),
    [role, permissions],
  );

  return (
    <header
      className={cn(
        "sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-border px-4 lg:px-6",
        "bg-background/85 backdrop-blur-md",
      )}
    >
      {/* Mobile nav toggle — hidden once the sidebar is in flow at md. */}
      <button
        type="button"
        onClick={onOpenMobileNav}
        aria-label="Open navigation"
        className="grid size-9 shrink-0 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 md:hidden"
      >
        <Menu className="size-5" />
      </button>

      {/* Workspace eyebrow (§4.2). */}
      <div className="hidden min-w-0 sm:block">
        <p className="text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
          Leadora workspace
        </p>
        <p className="truncate text-[13px] font-semibold text-foreground">
          {isFrontOffice ? "Front Office Desk" : "Hotel Sales CRM"}
        </p>
      </div>

      {/* Centre: command trigger. Hidden for FO per §4.2. */}
      {!isFrontOffice && (
        <div className="mx-auto hidden w-full max-w-md md:block">
          <button
            type="button"
            onClick={onOpenPalette}
            className={cn(
              "flex h-9 w-full items-center gap-2 rounded-md border border-border bg-surface px-3",
              "text-[13px] text-muted-foreground transition-colors",
              "hover:border-brand-300 hover:bg-surface-2 hover:text-foreground",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            )}
          >
            <Search className="size-4 shrink-0" />
            <span className="flex-1 truncate text-left">
              Search leads, deals, quotations…
            </span>
            <Kbd>⌘K</Kbd>
          </button>
        </div>
      )}

      <div className="ml-auto flex items-center gap-1.5">
        {/* Quick Add (§4.5). Hidden for FO. */}
        {!isFrontOffice && quickAdd.length > 0 && (
          <Popover open={quickAddOpen} onOpenChange={setQuickAddOpen}>
            <PopoverTrigger asChild>
              <Button
                size="md"
                variant="primary"
                leftIcon={<Plus className="size-4" />}
                rightIcon={<ChevronDown className="size-3.5 opacity-70" />}
              >
                <span className="hidden sm:inline">Create</span>
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-56">
              <p className="px-2 pb-1 pt-1.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
                Quick create
              </p>
              {quickAdd.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    key={item.href}
                    onClick={() => {
                      setQuickAddOpen(false);
                      router.push(item.href);
                    }}
                    className="flex w-full items-center gap-2.5 rounded-md px-2 py-2 text-left text-[13px] font-medium text-foreground transition-colors hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
                  >
                    <Icon className="size-4 text-muted-foreground" />
                    {item.label}
                  </button>
                );
              })}
            </PopoverContent>
          </Popover>
        )}

        {/* Theme toggle (§7). */}
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
          title={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
          className="grid size-9 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        >
          {theme === "dark" ? <Sun className="size-[18px]" /> : <Moon className="size-[18px]" />}
        </button>

        {/* Help (§4.7). */}
        <Popover open={helpOpen} onOpenChange={setHelpOpen}>
          <PopoverTrigger asChild>
            <button
              type="button"
              aria-label="Help"
              className="hidden size-9 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 sm:grid"
            >
              <HelpCircle className="size-[18px]" />
            </button>
          </PopoverTrigger>
          <PopoverContent align="end" className="w-56">
            <button
              onClick={() => {
                setHelpOpen(false);
                onOpenShortcuts();
              }}
              className="flex w-full items-center gap-2.5 rounded-md px-2 py-2 text-left text-[13px] font-medium text-foreground transition-colors hover:bg-surface-2"
            >
              <Keyboard className="size-4 text-muted-foreground" />
              <span className="flex-1">Keyboard shortcuts</span>
              <Kbd>?</Kbd>
            </button>
          </PopoverContent>
        </Popover>

        {notificationSlot}

        <div aria-hidden className="mx-1 h-6 w-px bg-border" />

        {/* User menu (§4.8). */}
        <Popover open={userMenuOpen} onOpenChange={setUserMenuOpen}>
          <PopoverTrigger asChild>
            <button
              type="button"
              aria-label="Account menu"
              className="flex items-center gap-2 rounded-md py-1 pl-1 pr-1.5 transition-colors hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              <Avatar src={avatarSource} name={user?.name} />
              <span className="hidden text-left leading-tight lg:block">
                <span className="block max-w-[10rem] truncate text-[12.5px] font-semibold text-foreground">
                  {user?.name ?? "Signed in"}
                </span>
                <span className="block text-[10.5px] text-muted-foreground">
                  {ROLE_LABEL[role]}
                </span>
              </span>
              <ChevronDown className="hidden size-3.5 text-muted-foreground lg:block" />
            </button>
          </PopoverTrigger>

          <PopoverContent align="end" className="w-64 p-0">
            <div className="flex items-center gap-3 border-b border-border p-3">
              <Avatar src={avatarSource} name={user?.name} size="lg" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-semibold text-foreground">
                  {user?.name}
                </p>
                <p className="truncate text-[11.5px] text-muted-foreground">
                  {user?.email}
                </p>
                <StatusPill tone="primary" dot={false} size="sm" className="mt-1.5">
                  {ROLE_LABEL[role]}
                </StatusPill>
              </div>
            </div>

            <div className="p-1.5">
              <MenuButton
                icon={<UserIcon className="size-4" />}
                label="Profile & preferences"
                onClick={() => {
                  setUserMenuOpen(false);
                  router.push(ROUTE_PATHS.profile);
                }}
              />
              <MenuButton
                icon={theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
                label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
                onClick={() => {
                  toggleTheme();
                  setUserMenuOpen(false);
                }}
              />
              <MenuButton
                icon={<Keyboard className="size-4" />}
                label="Keyboard shortcuts"
                onClick={() => {
                  setUserMenuOpen(false);
                  onOpenShortcuts();
                }}
              />
              <MenuButton
                icon={<Download className="size-4" />}
                label="APK Download"
                onClick={() => {
                  setUserMenuOpen(false);
                  window.open("https://github.com/dinhminhtien/leadora/releases/download/mobile-v1.0.0/app-release.apk", "_blank");
                }}
              />
              <div aria-hidden className="my-1 h-px bg-border" />
              <MenuButton
                icon={<LogOut className="size-4" />}
                label="Sign out"
                destructive
                onClick={() => {
                  setUserMenuOpen(false);
                  onLogout();
                }}
              />
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </header>
  );
}

function MenuButton({
  icon,
  label,
  onClick,
  destructive = false,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-md px-2 py-2 text-left text-[13px] font-medium transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
        destructive
          ? "text-danger hover:bg-danger/10"
          : "text-foreground hover:bg-surface-2",
      )}
    >
      <span className={destructive ? "text-danger" : "text-muted-foreground"}>{icon}</span>
      {label}
    </button>
  );
}

/**
 * Avatar with initials fallback. `src` may resolve from the app's
 * `local-storage-avatar://` scheme — that resolution stays in the layout, which
 * owns the user object.
 */
function Avatar({
  src,
  name,
  size = "md",
}: {
  src: string | null;
  name?: string;
  size?: "md" | "lg";
}) {
  const initials = (name ?? "?")
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const dim = size === "lg" ? "size-10" : "size-8";

  return src ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt=""
      aria-hidden
      className={cn(dim, "shrink-0 rounded-full border border-border object-cover")}
    />
  ) : (
    <span
      aria-hidden
      className={cn(
        dim,
        "grid shrink-0 place-items-center rounded-full bg-brand-500 font-bold text-brand-foreground",
        size === "lg" ? "text-[13px]" : "text-[11px]",
      )}
    >
      {initials}
    </span>
  );
}
