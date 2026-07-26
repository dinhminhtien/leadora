"use client";

import Link from "next/link";
import { KeyRound, CreditCard, ShieldCheck, ArrowUpRight, Sparkles } from "lucide-react";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { useAuthStore } from "@/stores/auth_store";

/**
 * Admin home (UC-6/7): account & role administration plus payment oversight.
 * Admin has no sales-workflow actions per the RBAC matrix, so this dashboard is
 * intentionally a focused console rather than a sales-pipeline view.
 */
const ADMIN_CARDS: { href: string; label: string; desc: string; Icon: React.ElementType; color: string }[] = [
  { href: ROUTE_PATHS.identityAccess, label: "User & Access Management", desc: "Create accounts, assign roles, configure permissions, activate/deactivate users", Icon: KeyRound,   color: "text-primary bg-primary/10 border-primary/20" },
  { href: ROUTE_PATHS.depositPayment, label: "Payment Oversight",        desc: "Review payment and deposit records across the system",                          Icon: CreditCard, color: "text-teal bg-teal/10 border-teal/20" },
];

export function AdminDashboardScreen() {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="space-y-6">
      {/* Welcome banner */}
      <div className="card-elev relative overflow-hidden p-6">
        <div className="absolute top-0 right-0 w-80 h-80 rounded-full bg-primary/8 blur-[80px] pointer-events-none" />
        <div className="relative z-10">
          <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-primary/10 border border-primary/20 text-[10px] font-bold text-primary tracking-wider uppercase mb-3">
            <ShieldCheck className="size-3" />
            <span>Admin Console</span>
          </div>
          <h1 className="text-xl md:text-2xl font-bold tracking-tight text-foreground">
            Welcome{user?.name ? `, ${user.name}` : ""}!
          </h1>
          <p className="text-xs text-muted-foreground mt-1">
            Manage user accounts, role permissions, and oversee system payment records.
          </p>
        </div>
      </div>

      {/* Admin function cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {ADMIN_CARDS.map((c) => (
          <Link key={c.href} href={c.href}
            className="group relative flex items-start gap-4 p-5 rounded-xl border border-border bg-card shadow-elev-1 hover:shadow-elev-2 hover:border-primary/20 transition-all duration-150">
            <span className={`flex items-center justify-center size-11 rounded-xl border shrink-0 ${c.color}`}>
              <c.Icon className="size-5" />
            </span>
            <div className="flex-1">
              <p className="text-sm font-bold text-foreground">{c.label}</p>
              <p className="text-xs text-muted-foreground mt-1 leading-relaxed">{c.desc}</p>
            </div>
            <ArrowUpRight className="size-4 text-muted-foreground/50 group-hover:text-primary transition shrink-0" />
          </Link>
        ))}
      </div>

      <div className="flex items-center gap-2 text-[11px] text-muted-foreground px-1">
        <Sparkles className="size-3.5" />
        Admin accounts do not have access to the sales workflow (leads, deals, quotations, bookings).
      </div>
    </div>
  );
}
