"use client";

/**
 * Admin home (UC-6/7) — Website UI Blueprint §8.3.
 *
 * Account & role administration plus payment oversight. Admin has no
 * sales-workflow actions per the RBAC matrix (§14), so this is deliberately a
 * focused console rather than a pipeline view — the footnote at the bottom says
 * so explicitly, because an empty sales dashboard would otherwise read as a bug.
 */

import Link from "next/link";
import { ArrowUpRight, CreditCard, KeyRound, ShieldCheck, Sparkles } from "lucide-react";

import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { useAuthStore } from "@/stores/auth_store";
import { GreetingBar } from "@/components/ui/kpi-card";
import { cn } from "@/lib/utils";

/**
 * Two wide cards rather than the 5-up `ToolCardGrid`: Admin has only two
 * destinations, and each needs a full sentence of description. Squeezing them
 * into the compact grid would leave three empty columns.
 */
const ADMIN_CARDS: {
  href: string;
  label: string;
  description: string;
  icon: React.ElementType;
  accent: string;
}[] = [
  {
    href: ROUTE_PATHS.identityAccess,
    label: "User & Access Management",
    description:
      "Create accounts, assign roles, configure permissions, activate or deactivate users.",
    icon: KeyRound,
    accent: "border-brand-500/20 bg-brand-500/10 text-brand-600 dark:text-brand-500",
  },
  {
    href: ROUTE_PATHS.depositPayment,
    label: "Payment Oversight",
    description: "Review payment and deposit records across the system.",
    icon: CreditCard,
    accent: "border-teal/20 bg-teal/10 text-teal",
  },
];

export function AdminDashboardScreen() {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="space-y-6">
      <GreetingBar
        name={user?.name}
        roleLabel="Admin console"
        subtitle="Manage user accounts, role permissions and system payment records."
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {ADMIN_CARDS.map((card) => {
          const Icon = card.icon;
          return (
            <Link
              key={card.href}
              href={card.href}
              className={cn(
                "card-surface group flex items-start gap-4 p-5",
                "transition-[box-shadow,border-color] duration-[120ms]",
                "hover:border-brand-300 hover:shadow-elev-2",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              )}
            >
              <span
                aria-hidden
                className={cn(
                  "grid size-11 shrink-0 place-items-center rounded-lg border",
                  card.accent,
                )}
              >
                <Icon className="size-5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[14px] font-semibold text-foreground">
                  {card.label}
                </span>
                <span className="mt-1 block text-[12.5px] leading-relaxed text-muted-foreground">
                  {card.description}
                </span>
              </span>
              <ArrowUpRight
                aria-hidden
                className="size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-brand-500"
              />
            </Link>
          );
        })}
      </div>

      <p className="flex items-center gap-2 px-1 text-[11.5px] text-muted-foreground">
        <Sparkles aria-hidden className="size-3.5" />
        Admin accounts do not have access to the sales workflow (leads, deals,
        quotations, bookings).
      </p>
    </div>
  );
}
