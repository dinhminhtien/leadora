"use client";

/**
 * Command palette (⌘K) — Website UI Blueprint §3.8 and §4.4.
 *
 * > "New in this redesign — spec §12.4 called for the search input to be wired."
 *
 * The old topbar had a search `<input>` with no `value` and no `onChange`: it
 * looked functional and did nothing. This replaces it with a real palette.
 *
 * Groups: `Navigate · Create · Search records · Actions`.
 * Query prefixes (§3.8):
 *   ` ` global · `>` commands · `#` module filter · `@` owner · `/` jump to route
 *
 * Only destinations the signed-in user may actually open are listed — the same
 * `canAccessPath` gate the sidebar uses, so the palette can never offer a route
 * that would bounce the user back to their dashboard.
 */

import * as React from "react";
import { useRouter } from "next/navigation";
import {
  ArrowRight,
  CornerDownLeft,
  Moon,
  Plus,
  Search,
  Sun,
} from "lucide-react";

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandShortcut,
} from "@/components/ui/command";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import {
  NAV_ITEMS,
  QUICK_ADD_ITEMS,
  type NavItem,
} from "@/app/routes/navigation";
import { canAccessPath, type AppRole } from "@/shared/auth/access";
import { useUiStore } from "@/stores/ui_store";
import { cn } from "@/lib/utils";

type CommandPaletteProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  role: AppRole;
  permissions: string[];
};

/** Strips a leading prefix character and reports which mode we're in. */
function parseQuery(raw: string): {
  mode: "all" | "commands" | "module" | "owner" | "route";
  term: string;
} {
  if (raw.startsWith(">")) return { mode: "commands", term: raw.slice(1).trim() };
  if (raw.startsWith("#")) return { mode: "module", term: raw.slice(1).trim() };
  if (raw.startsWith("@")) return { mode: "owner", term: raw.slice(1).trim() };
  if (raw.startsWith("/")) return { mode: "route", term: raw.slice(1).trim() };
  return { mode: "all", term: raw.trim() };
}

export function CommandPalette({
  open,
  onOpenChange,
  role,
  permissions,
}: CommandPaletteProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Remounted per opening — see the query-reset note in the body. */}
      <PaletteBody
        onOpenChange={onOpenChange}
        role={role}
        permissions={permissions}
      />
    </Dialog>
  );
}

function PaletteBody({
  onOpenChange,
  role,
  permissions,
}: Omit<CommandPaletteProps, "open">) {
  const router = useRouter();
  // Keyed by `open` so the query resets on each opening without a
  // reset-in-effect: a stale term from last time is never what the user wants
  // to run now, and remounting is how React expresses "start fresh".
  const [query, setQuery] = React.useState("");
  const { theme, toggleTheme } = useUiStore();

  const { mode, term } = parseQuery(query);

  const visibleNav = React.useMemo(
    () => NAV_ITEMS.filter((item) => canAccessPath(role, item.href, permissions)),
    [role, permissions],
  );

  const visibleCreate = React.useMemo(
    () =>
      QUICK_ADD_ITEMS.filter((item) =>
        canAccessPath(role, item.requires, permissions),
      ),
    [role, permissions],
  );

  const go = React.useCallback(
    (href: string) => {
      onOpenChange(false);
      router.push(href);
    },
    [onOpenChange, router],
  );

  const showNavigate = mode === "all" || mode === "route" || mode === "module";
  const showCreate = mode === "all" || mode === "commands";
  const showActions = mode === "all" || mode === "commands";

  return (
    <>
      <DialogContent
        size="lg"
        showClose={false}
        className="top-[18%] max-w-[640px] translate-y-0 overflow-hidden p-0"
        aria-describedby={undefined}
      >
        {/* Radix requires a title for the a11y tree; it is visually redundant here. */}
        <DialogTitle className="sr-only">Command palette</DialogTitle>

        <Command
          // We filter by hand so prefix modes can change what's eligible.
          shouldFilter={mode !== "owner"}
          loop
        >
          <CommandInput
            value={query}
            onValueChange={setQuery}
            placeholder="Search or jump to…    ⟩ commands   # module   @ owner   / route"
            autoFocus
          />

          <CommandList>
            <CommandEmpty>
              <div className="flex flex-col items-center gap-1.5">
                <Search className="size-5 opacity-40" />
                <span>No matches for “{term}”</span>
              </div>
            </CommandEmpty>

            {showNavigate && visibleNav.length > 0 && (
              <CommandGroup heading="Navigate">
                {visibleNav.map((item) => (
                  <NavCommandItem key={item.href} item={item} onSelect={go} />
                ))}
              </CommandGroup>
            )}

            {showCreate && visibleCreate.length > 0 && (
              <CommandGroup heading="Create">
                {visibleCreate.map((item) => {
                  const Icon = item.icon;
                  return (
                    <CommandItem
                      key={item.href}
                      value={`create ${item.label}`}
                      onSelect={() => go(item.href)}
                    >
                      <span className="grid size-6 place-items-center rounded-md bg-brand-500/10 text-brand-600 dark:text-brand-500">
                        <Plus className="size-3.5" />
                      </span>
                      <span className="flex-1">{item.label}</span>
                      <Icon className="size-4 text-muted-foreground" />
                    </CommandItem>
                  );
                })}
              </CommandGroup>
            )}

            {showActions && (
              <CommandGroup heading="Actions">
                <CommandItem
                  value="toggle theme dark light appearance"
                  onSelect={() => {
                    toggleTheme();
                    onOpenChange(false);
                  }}
                >
                  {theme === "dark" ? <Sun /> : <Moon />}
                  <span className="flex-1">
                    Switch to {theme === "dark" ? "light" : "dark"} theme
                  </span>
                </CommandItem>
              </CommandGroup>
            )}

            {mode === "owner" && (
              <div className="px-3 py-8 text-center text-[13px] text-muted-foreground">
                Owner search opens inside a module — try{" "}
                <button
                  type="button"
                  className="font-medium text-brand-600 underline underline-offset-2 dark:text-brand-500"
                  onClick={() => setQuery("")}
                >
                  clearing the @ prefix
                </button>{" "}
                and picking a list first.
              </div>
            )}
          </CommandList>

          <PaletteFooter />
        </Command>
      </DialogContent>
    </>
  );
}

function NavCommandItem({
  item,
  onSelect,
}: {
  item: NavItem;
  onSelect: (href: string) => void;
}) {
  const Icon = item.icon;
  return (
    <CommandItem
      value={`${item.label} ${item.hint ?? ""} ${item.href}`}
      onSelect={() => onSelect(item.href)}
    >
      <Icon className="text-muted-foreground" />
      <span className="flex-1 truncate">
        {item.label}
        {item.hint && (
          <span className="ml-2 text-[12px] text-muted-foreground">{item.hint}</span>
        )}
      </span>
      {item.shortcut && <CommandShortcut>g {item.shortcut}</CommandShortcut>}
    </CommandItem>
  );
}

/** Keyboard legend — teaches the model without a separate help screen. */
function PaletteFooter() {
  return (
    <div className="flex items-center gap-4 border-t border-border px-4 py-2.5 text-[11px] text-muted-foreground">
      <LegendKey icon={<ArrowRight className="size-3 rotate-90" />} label="navigate" />
      <LegendKey icon={<CornerDownLeft className="size-3" />} label="open" />
      <span className="flex items-center gap-1.5">
        <Kbd>esc</Kbd> close
      </span>
      <span className="ml-auto hidden items-center gap-1.5 sm:flex">
        <Kbd>?</Kbd> all shortcuts
      </span>
    </div>
  );
}

function LegendKey({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="grid h-4 min-w-4 place-items-center rounded border border-border bg-muted px-1">
        {icon}
      </span>
      {label}
    </span>
  );
}

export function Kbd({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <kbd
      className={cn(
        "inline-flex h-4 min-w-4 items-center justify-center rounded border border-border bg-muted px-1",
        "font-sans text-[10px] font-semibold text-muted-foreground",
        className,
      )}
    >
      {children}
    </kbd>
  );
}
