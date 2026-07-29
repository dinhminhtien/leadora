"use client";

/**
 * Lead detail drawer — Website UI Blueprint §2.11 / §10.4, structured after the
 * `leadora-flow` Lead detail sheet.
 *
 * This is the **full** lead record, not a preview: Overview / Edit / Activity
 * tabs, the pipeline stepper, a working edit form, status transitions and the
 * real Convert flow all live here. A rep never has to leave the list — which
 * matters because leaving it discards the filters, page and scroll position they
 * built up.
 *
 * **This is the only place a lead opens.** There used to be a second, full-page
 * detail screen at `/leads/{id}` showing the same record in a different layout;
 * it was deleted. The URL still resolves — deep-links from notifications, SLA
 * breaches, tasks and customer profiles all point at a lead — but it now
 * redirects to `/leads?lead={id}`, which opens this drawer. One record, one UI.
 *
 * **Rules are mirrored, never re-implemented.** Validation, the status ladder and
 * the convert gate all come from `lead-rules.ts`, which is shared with the create
 * drawer and the detail page, so the three surfaces cannot disagree. Controls the
 * server would refuse are hidden rather than shown and then 403'd (§12.13):
 * a CONVERTED lead is a locked historical record (BR-08), a LOST lead is
 * terminal, and an unassigned lead cannot convert (`LEAD_UNASSIGNED`).
 */

import * as React from "react";
import { Building2, User } from "lucide-react";

import {
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { StatusPill } from "@/components/ui/status-pill";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
import { initialsOf } from "@/shared/utils/avatar";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import { useUsers } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { ConvertModal } from "@/features/lead/components/ConvertModal";
import {
  ActivityTab,
  EditTab,
  LeadDetailTabs,
  OverviewTab,
  type TabId,
} from "@/features/lead/components/lead-detail-parts";
import { useLeadDetail } from "@/features/lead/hooks/use_leads";
import { canConvertLead, isLeadLocked } from "@/features/lead/lib/lead-rules";
import type { Lead } from "@/services/lead_service";
import type { UserSummary } from "@/services/follow_up_task_service";

export function LeadDetailDrawer({
  lead,
  onOpenChange,
}: {
  lead: Lead | null;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Drawer open={!!lead} onOpenChange={onOpenChange}>
      <DrawerContent size="lg" className="gap-0">
        {/* Keyed by id so switching leads resets tab and form state — otherwise
            a half-typed edit would carry over onto the next record. */}
        {lead && (
          <LeadDetailBody key={lead.leadId} row={lead} onClose={() => onOpenChange(false)} />
        )}
      </DrawerContent>
    </Drawer>
  );
}

/**
 * `row` is the record as the *list* had it when the user clicked — a snapshot, and the reason an
 * edit used to need a close-and-reopen to show up. Saving invalidates the queries, the table
 * behind repaints, but a plain object held in the caller's state cannot: it is not subscribed to
 * anything. So the body reads the lead from the cache instead and keeps the row only as the first
 * paint, which is what makes the drawer open with content rather than a spinner.
 */
function LeadDetailBody({ row, onClose }: { row: Lead; onClose: () => void }) {
  const [tab, setTab] = React.useState<TabId>("overview");
  const [convertOpen, setConvertOpen] = React.useState(false);

  // A 403/404 here (reassigned away, deleted under us) leaves `data` undefined and simply falls
  // back to the row — the drawer keeps showing what the list showed rather than blanking out.
  const { data: detail } = useLeadDetail(row.leadId);
  const lead = detail?.data ?? row;

  const role = getUserRole(useAuthStore((s) => s.user));
  const canAssign = role === "MANAGER" || role === "ADMIN";
  // Narrower than `canAssign` on purpose: `POST /leads/{id}/reopen` is
  // `hasRole('MANAGER')`, and an Admin has no business in the sales screens
  // (see `AccessExpressions`). Offering the button to a role the route refuses
  // would put a control on screen whose only outcome is a 403.
  const canReopen = role === "MANAGER";

  const { data: usersResp } = useUsers();
  const salesUsers: UserSummary[] = (usersResp?.data ?? []).filter(
    (u) => (u.roleName ?? "").toUpperCase() === "SALES",
  );

  const locked = isLeadLocked(lead.status);
  const convertible = canConvertLead(lead);

  const tabs: { id: TabId; label: string }[] = [
    { id: "overview", label: "Overview" },
    // A locked lead has nothing to edit, so the tab is not offered at all.
    ...(locked ? [] : [{ id: "edit" as const, label: "Edit" }]),
    { id: "activity", label: "Activity" },
  ];

  return (
    <>
      <DrawerHeader className="gap-3">
        <div className="flex items-start gap-3">
          <span
            aria-hidden
            className="grid size-11 shrink-0 place-items-center rounded-full bg-brand-500/12 text-[14px] font-bold text-brand-600 dark:text-brand-500"
          >
            {initialsOf(lead.fullName)}
          </span>
          <div className="min-w-0 flex-1">
            <DrawerTitle className="truncate text-[18px] leading-6">
              {lead.fullName}
            </DrawerTitle>
            <DrawerDescription className="flex items-center gap-1.5">
              {lead.isCorporate ? (
                <>
                  <Building2 className="size-3.5 shrink-0" />
                  <span className="truncate">{lead.companyName || "Organization"}</span>
                </>
              ) : (
                <>
                  <User className="size-3.5 shrink-0" />
                  Individual
                </>
              )}
            </DrawerDescription>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <StatusPill size="sm" domain="lead" value={lead.status} />
          <SlaStatusBadge entityId={lead.leadId} entityType="LEAD" />
        </div>

        <div className="mt-1">
          <LeadDetailTabs tabs={tabs} active={tab} onChange={setTab} />
        </div>
      </DrawerHeader>

      <DrawerBody className="space-y-5">
        {tab === "overview" && (
          <OverviewTab
            lead={lead}
            locked={locked}
            convertible={convertible}
            canReopen={canReopen}
            onEdit={() => setTab("edit")}
            onConvert={() => setConvertOpen(true)}
          />
        )}

        {tab === "edit" && (
          <EditTab
            lead={lead}
            canAssign={canAssign}
            salesUsers={salesUsers}
            onCancel={() => setTab("overview")}
            onSaved={() => setTab("overview")}
          />
        )}

        {tab === "activity" && <ActivityTab lead={lead} />}
      </DrawerBody>

      {/* No "Open in full page" any more — there is no full page to open. The
          record reference stays: it is what a rep quotes on the phone. */}
      <DrawerFooter>
        <span className="numeric text-[11px] text-muted-foreground">
          {lead.leadId.slice(0, 8).toUpperCase()}
        </span>
      </DrawerFooter>

      {convertOpen && (
        <ConvertModal
          lead={lead}
          onClose={() => {
            setConvertOpen(false);
            // The lead is CONVERTED and locked now; the list behind has already
            // been invalidated by the mutation, so close rather than show a stale
            // record.
            onClose();
          }}
        />
      )}
    </>
  );
}
