"use client";

/**
 * The lead detail *body* — pipeline stepper, quick actions, Overview / Edit /
 * Activity. Rendered by `LeadDetailDrawer`, which is now the only place a lead
 * opens.
 *
 * **Why it is a separate file.** There used to be a second surface: a full-page
 * `LeadDetailScreen` at `/leads/{id}` showing the same record. The two were
 * independent screens — the drawer read the shared `lead-rules.ts`, the page
 * kept its own `PIPELINE` / `STATUS_LABEL` / `allowedStatusOptions` copies and a
 * hard-coded `MOCK_LOGS` timeline — and they had drifted on what a lead even
 * looks like. They were merged onto this body, and then the page itself was
 * dropped so there is one lead UI rather than two.
 *
 * **`wide` is what is left of the page.** It lays the Overview sections side by
 * side instead of stacking them. Nothing passes it today; it is kept because the
 * split it encodes (narrow column vs. rectangle) is a layout choice, not a
 * second implementation, and re-adding a wide frame costs one prop.
 */

import * as React from "react";
import {
  Activity,
  Building2,
  CalendarDays,
  Check,
  Mail,
  MapPin,
  Pencil,
  Phone,
  Sparkles,
  User,
  X,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { StatusPill } from "@/components/ui/status-pill";
import { toast } from "@/stores/toast_store";
import { resolveApiError } from "@/shared/design/error-messages";
import { useUpdateLead } from "@/features/lead/hooks/use_leads";
import {
  LEAD_PIPELINE,
  LEAD_STATUS_LABEL,
  allowedStatusOptions,
  hasErrors,
  toUpdatePayload,
  validateLead,
  type LeadFieldErrors,
} from "@/features/lead/lib/lead-rules";
import type { Lead, LeadStatus, UpdateLeadPayload } from "@/services/lead_service";
import type { UserSummary } from "@/services/follow_up_task_service";

export const SOURCE_OPTIONS = [
  "Website Inquiry",
  "Referral",
  "Social Media",
  "Cold Call",
  "Walk-in",
  "Event",
];

export type TabId = "overview" | "edit" | "activity";

/* ------------------------------------------------------------------ *
 * Presentation primitives
 * ------------------------------------------------------------------ */

export function DetailSection({
  title,
  className,
  children,
}: {
  title: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <section className={cn("space-y-2", className)}>
      <h3 className="text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
        {title}
      </h3>
      <div className="overflow-hidden rounded-lg border border-border">{children}</div>
    </section>
  );
}

export function DetailRow({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-border px-3 py-2.5 last:border-b-0">
      <span className="flex shrink-0 items-center gap-1.5 text-[12px] text-muted-foreground">
        {Icon && <Icon className="size-3.5" />}
        {label}
      </span>
      <span className="min-w-0 text-right text-[12.5px] text-foreground">
        {value ?? <Empty />}
      </span>
    </div>
  );
}

/** Missing data reads as absent, not as a real value. */
export function Empty() {
  return <span className="text-muted-foreground">—</span>;
}

export function formatDate(iso?: string | null): React.ReactNode {
  if (!iso) return <Empty />;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return <Empty />;
  return d.toLocaleDateString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

function FieldLabel({
  children,
  required,
}: {
  children: React.ReactNode;
  required?: boolean;
}) {
  return (
    <label className="mb-1 block text-[12px] font-medium text-foreground">
      {children}
      {required && <span className="ml-0.5 text-danger">*</span>}
    </label>
  );
}

const SELECT_CLASS =
  "h-9 w-full rounded-md border border-input bg-surface px-2 text-[13px] text-foreground " +
  "focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30";

/* ------------------------------------------------------------------ *
 * Header pieces
 * ------------------------------------------------------------------ */

/**
 * Pipeline stepper. `LOST` is deliberately not a step — it is an exit from the
 * ladder, not a position on it, so it renders as its own banner instead of
 * pretending to be stage 5.
 */
export function PipelineStepper({ status }: { status: LeadStatus }) {
  if (status === "LOST") {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-danger/30 bg-danger-bg/40 px-3 py-2">
        <X className="size-4 shrink-0 text-danger" />
        <span className="text-[12.5px] font-medium text-danger">
          Closed as lost — this lead is no longer in the pipeline.
        </span>
      </div>
    );
  }

  const currentIndex = LEAD_PIPELINE.findIndex((s) => s.status === status);

  return (
    <ol className="flex items-center gap-1">
      {LEAD_PIPELINE.map((step, i) => {
        const done = i < currentIndex;
        const active = i === currentIndex;
        return (
          <li key={step.status} className="flex min-w-0 flex-1 items-center gap-1">
            <span
              className={cn(
                "grid size-5 shrink-0 place-items-center rounded-full text-[10px] font-bold",
                done && "bg-success/15 text-success",
                active && "bg-brand-500 text-brand-foreground",
                !done && !active && "bg-muted text-muted-foreground",
              )}
            >
              {done ? <Check className="size-3" strokeWidth={3} /> : i + 1}
            </span>
            <span
              className={cn(
                "min-w-0 truncate text-[11px]",
                active ? "font-semibold text-foreground" : "text-muted-foreground",
              )}
            >
              {step.label}
            </span>
            {i < LEAD_PIPELINE.length - 1 && (
              <span
                aria-hidden
                className={cn(
                  "h-px min-w-2 flex-1",
                  done ? "bg-success/40" : "bg-border",
                )}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}

/**
 * Overview / Edit / Activity.
 *
 * `wide` is the whole difference between the two frames: in the drawer the tabs
 * split a narrow column evenly (`flex-1`), on the page they size to their labels
 * — three buttons stretched across 1400px read as a toolbar, not a tab strip.
 */
export function LeadDetailTabs({
  tabs,
  active,
  wide,
  onChange,
}: {
  tabs: { id: TabId; label: string }[];
  active: TabId;
  wide?: boolean;
  onChange: (id: TabId) => void;
}) {
  return (
    <div
      role="tablist"
      aria-label="Lead detail sections"
      className={cn(
        "flex items-center gap-0.5 rounded-md border border-border bg-muted p-0.5",
        wide && "w-fit",
      )}
    >
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          aria-selected={active === t.id}
          onClick={() => onChange(t.id)}
          className={cn(
            "rounded px-2.5 py-1 text-[12.5px] font-medium transition-colors",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            wide ? "px-5" : "flex-1",
            active === t.id
              ? "bg-surface text-foreground shadow-elev-1"
              : "text-muted-foreground hover:text-foreground",
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Overview
 * ------------------------------------------------------------------ */

export function OverviewTab({
  lead,
  locked,
  convertible,
  wide,
  onEdit,
  onConvert,
}: {
  lead: Lead;
  locked: boolean;
  convertible: boolean;
  /** Lay the sections out side by side instead of stacking them. */
  wide?: boolean;
  onEdit: () => void;
  onConvert: () => void;
}) {
  const status = (lead.status ?? "").toUpperCase() as LeadStatus;

  return (
    <div className="space-y-5">
      <PipelineStepper status={status} />

      {/* Quick actions. `tel:` / `mailto:` are inert without a value, so those
          buttons only render when the lead actually has that channel. */}
      <div className="flex flex-wrap gap-2">
        {!locked && (
          <Button
            size="sm"
            variant="secondary"
            leftIcon={<Pencil className="size-3.5" />}
            onClick={onEdit}
          >
            Edit
          </Button>
        )}
        {lead.phone && (
          <Button
            size="sm"
            variant="secondary"
            leftIcon={<Phone className="size-3.5" />}
            onClick={() => {
              window.location.href = `tel:${lead.phone}`;
            }}
          >
            Call
          </Button>
        )}
        {lead.email && (
          <Button
            size="sm"
            variant="secondary"
            leftIcon={<Mail className="size-3.5" />}
            onClick={() => {
              window.location.href = `mailto:${lead.email}`;
            }}
          >
            Email
          </Button>
        )}
        {convertible && (
          <Button
            size="sm"
            variant="success"
            leftIcon={<Sparkles className="size-3.5" />}
            onClick={onConvert}
          >
            Convert
          </Button>
        )}
      </div>

      {/* Say *why* Convert is unavailable rather than just omitting the button. */}
      {!locked && !convertible && (
        <p className="rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-[12px] text-warning">
          Assign this lead to a sales rep before it can be converted.
        </p>
      )}
      {locked && (
        <p className="rounded-md border border-border bg-muted px-3 py-2 text-[12px] text-muted-foreground">
          {status === "CONVERTED"
            ? "This lead has been converted and is kept as a locked historical record."
            : "This lead is closed as lost and can no longer be edited."}
        </p>
      )}

      {/* The three sections stack in the drawer and sit side by side on the page.
          `items-start` stops a short section stretching to match a tall neighbour. */}
      <div
        className={cn(
          "grid gap-5",
          wide && "items-start md:grid-cols-2 xl:grid-cols-3",
        )}
      >
        <DetailSection title="Contact">
          <DetailRow
            label="Email"
            icon={Mail}
            value={
              lead.email ? (
                <a
                  href={`mailto:${lead.email}`}
                  className="truncate text-brand-600 hover:underline dark:text-brand-500"
                >
                  {lead.email}
                </a>
              ) : null
            }
          />
          <DetailRow
            label="Phone"
            icon={Phone}
            value={
              lead.phone ? (
                <a
                  href={`tel:${lead.phone}`}
                  className="text-brand-600 hover:underline dark:text-brand-500"
                >
                  {lead.phone}
                </a>
              ) : null
            }
          />
          <DetailRow label="Address" icon={MapPin} value={lead.address || null} />
          {lead.isCorporate && (
            <DetailRow label="Company" icon={Building2} value={lead.companyName || null} />
          )}
        </DetailSection>

        <DetailSection title="Qualification">
          <DetailRow label="Source" value={lead.source || null} />
          <DetailRow label="Interested service" value={lead.interestedService || null} />
          <DetailRow
            label="Status"
            value={<StatusPill size="sm" domain="lead" value={lead.status} />}
          />
        </DetailSection>

        <DetailSection title="Ownership & activity">
          <DetailRow
            label="Owner"
            icon={User}
            value={
              lead.assignedUserName ?? (
                <span className="italic text-muted-foreground">Unassigned</span>
              )
            }
          />
          <DetailRow label="Created by" value={lead.createdByName || null} />
          <DetailRow label="Created" icon={CalendarDays} value={formatDate(lead.createdAt)} />
          <DetailRow label="Last updated" value={formatDate(lead.updatedAt)} />
          {lead.convertedAt && (
            <DetailRow label="Converted" value={formatDate(lead.convertedAt)} />
          )}
        </DetailSection>

        {lead.notes && (
          <DetailSection
            title="Notes"
            className={cn(wide && "md:col-span-2 xl:col-span-3")}
          >
            <p className="whitespace-pre-wrap px-3 py-2.5 text-[12.5px] leading-relaxed text-foreground">
              {lead.notes}
            </p>
          </DetailSection>
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Edit
 * ------------------------------------------------------------------ */

export function EditTab({
  lead,
  canAssign,
  salesUsers,
  onCancel,
  onSaved,
}: {
  lead: Lead;
  canAssign: boolean;
  salesUsers: UserSummary[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const updateMutation = useUpdateLead(lead.leadId);

  const [form, setForm] = React.useState<UpdateLeadPayload>({
    fullName: lead.fullName,
    email: lead.email ?? "",
    phone: lead.phone ?? "",
    companyName: lead.companyName ?? "",
    address: lead.address ?? "",
    isCorporate: lead.isCorporate,
    source: lead.source ?? "",
    interestedService: lead.interestedService ?? "",
    notes: lead.notes ?? "",
    status: lead.status,
    assignedUserId: lead.assignedUserId ?? "",
  });
  const [errors, setErrors] = React.useState<LeadFieldErrors>({});
  const [serverError, setServerError] = React.useState("");

  const set = <K extends keyof UpdateLeadPayload>(
    key: K,
    value: UpdateLeadPayload[K],
  ) => setForm((f) => ({ ...f, [key]: value }));

  const statusOptions = allowedStatusOptions(lead.status);

  /**
   * BR-05 — what a lead still needs before it can enter active follow-up.
   *
   * Read from the *form*, not the lead, so it clears the moment the user types
   * into the boxes above rather than after a save. Without this the move to
   * Contacted/Qualified failed server-side with a 422 that pointed at no field.
   * The names are short because they are printed inside the `<option>` itself:
   * opening the dropdown covers the hint below it, so at the exact moment the
   * user asks "why can't I pick this?" the answer would be behind the menu.
   */
  const missingForFollowUp = [
    !form.phone?.trim() && !form.email?.trim() ? "phone or email" : null,
    !form.source?.trim() ? "source" : null,
    !form.interestedService?.trim() ? "interested service" : null,
  ].filter(Boolean) as string[];

  // BR-06: an unassigned lead is a draft — it stays NEW until a manager gives it
  // to someone, so no forward move is available at all.
  const unassigned = !form.assignedUserId;
  const forwardBlocked = unassigned || missingForFollowUp.length > 0;
  // Lost is always reachable, and the current value must stay selectable.
  const isForwardMove = (s: LeadStatus) => s !== lead.status && s !== "LOST";

  const statusHint = unassigned
    ? "This lead must be assigned to a sales rep before its status can change."
    : missingForFollowUp.length
      ? `To move this lead to Contacted or Qualified, fill in ${missingForFollowUp.join(", ")} in this form — the option unlocks as soon as you type. You can still mark it Lost.`
      : "Status only moves forward (New → Contacted → Qualified). “Converted” is set automatically during conversion.";

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const next = validateLead(form);
    setErrors(next);
    if (hasErrors(next)) return;

    setServerError("");
    updateMutation.mutate(toUpdatePayload(form), {
      onSuccess: () => {
        toast.success("Lead updated");
        onSaved();
      },
      onError: (err) => setServerError(resolveApiError(err).message),
    });
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <FieldLabel required>Full name</FieldLabel>
        <Input
          maxLength={40}
          value={form.fullName ?? ""}
          onChange={(e) => set("fullName", e.target.value)}
          error={errors.fullName}
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <FieldLabel>Email</FieldLabel>
          <Input
            maxLength={40}
            value={form.email ?? ""}
            onChange={(e) => set("email", e.target.value)}
            error={errors.email}
          />
        </div>
        <div>
          <FieldLabel>Phone</FieldLabel>
          <Input
            phoneOnly
            value={form.phone ?? ""}
            onChange={(e) => set("phone", e.target.value)}
            error={errors.phone}
          />
        </div>
      </div>

      {/* Individual vs organization — drives the company-name requirement. */}
      <div>
        <FieldLabel>Customer type</FieldLabel>
        <div className="grid grid-cols-2 gap-2">
          {[
            { corporate: false, label: "Individual", Icon: User },
            { corporate: true, label: "Organization", Icon: Building2 },
          ].map(({ corporate, label, Icon }) => (
            <button
              key={label}
              type="button"
              onClick={() => set("isCorporate", corporate)}
              className={cn(
                "flex items-center justify-center gap-1.5 rounded-md border py-2 text-[12.5px] font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                !!form.isCorporate === corporate
                  ? "border-brand-500 bg-brand-500/10 text-brand-600 dark:text-brand-500"
                  : "border-border text-muted-foreground hover:border-brand-300",
              )}
            >
              <Icon className="size-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      {form.isCorporate && (
        <div>
          <FieldLabel required>Company / organization</FieldLabel>
          <Input
            maxLength={40}
            value={form.companyName ?? ""}
            onChange={(e) => set("companyName", e.target.value)}
            error={errors.companyName}
          />
        </div>
      )}

      <div>
        <FieldLabel>Address</FieldLabel>
        <Input
          value={form.address ?? ""}
          onChange={(e) => set("address", e.target.value)}
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <FieldLabel>Source</FieldLabel>
          <select
            value={form.source ?? ""}
            onChange={(e) => set("source", e.target.value)}
            className={SELECT_CLASS}
          >
            <option value="">—</option>
            {SOURCE_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>

        {/*
          Only the current stage, the single next stage and Lost are offered —
          `UpdateLeadUseCase` rejects anything else with LEAD_INVALID_TRANSITION,
          and CONVERTED is reachable only through the conversion flow.
        */}
        <div>
          <FieldLabel>Status</FieldLabel>
          <select
            value={form.status ?? lead.status}
            onChange={(e) => set("status", e.target.value as LeadStatus)}
            className={SELECT_CLASS}
          >
            {statusOptions.map((s) => {
              const blocked = forwardBlocked && isForwardMove(s);
              return (
                <option key={s} value={s} disabled={blocked}>
                  {LEAD_STATUS_LABEL[s]}
                  {blocked && !unassigned && ` — needs ${missingForFollowUp.join(", ")}`}
                  {blocked && unassigned && " — assign this lead first"}
                </option>
              );
            })}
          </select>
          <p className="mt-1 text-[11.5px] text-muted-foreground">{statusHint}</p>
        </div>
      </div>

      <div>
        <FieldLabel>Interested service</FieldLabel>
        <Input
          maxLength={100}
          value={form.interestedService ?? ""}
          onChange={(e) => set("interestedService", e.target.value)}
        />
      </div>

      {/* Reassignment reaches into someone else's work, so it is Manager/Admin
          only — the same gate `hasFullAccess` applies elsewhere. */}
      {canAssign && (
        <div>
          <FieldLabel>Assigned to</FieldLabel>
          <select
            value={form.assignedUserId ?? ""}
            onChange={(e) => set("assignedUserId", e.target.value)}
            className={SELECT_CLASS}
          >
            <option value="">Unassigned</option>
            {salesUsers.map((u) => (
              <option key={u.userId} value={u.userId}>
                {u.fullName}
              </option>
            ))}
          </select>
        </div>
      )}

      <div>
        <FieldLabel>Notes</FieldLabel>
        <textarea
          rows={3}
          value={form.notes ?? ""}
          onChange={(e) => set("notes", e.target.value)}
          className="w-full resize-y rounded-md border border-input bg-surface px-3 py-2 text-[13px] text-foreground focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
        />
      </div>

      {serverError && (
        <p
          role="alert"
          className="rounded-md border border-danger/40 bg-danger-bg/50 px-3 py-2 text-[12.5px] text-danger"
        >
          {serverError}
        </p>
      )}

      <div className="flex items-center justify-end gap-2 border-t border-border pt-3">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onCancel}
          disabled={updateMutation.isPending}
        >
          Cancel
        </Button>
        <Button type="submit" variant="primary" size="sm" isLoading={updateMutation.isPending}>
          Save changes
        </Button>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------------ *
 * Activity
 * ------------------------------------------------------------------ */

export function ActivityTab({ lead }: { lead: Lead }) {
  /**
   * Derived from the lead's own timestamps rather than invented.
   *
   * The lead endpoints expose no activity feed, and the interaction timeline is
   * keyed by linked record rather than by lead alone — so rather than render
   * placeholder rows that look like real history, this shows only the events the
   * record can actually prove, and points at the timeline for the rest.
   */
  const events = [
    lead.convertedAt && {
      at: lead.convertedAt,
      text: "Converted to a customer",
    },
    lead.updatedAt &&
      lead.updatedAt !== lead.createdAt && {
        at: lead.updatedAt,
        text: "Lead details last updated",
      },
    lead.createdAt && {
      at: lead.createdAt,
      text: lead.source
        ? `Lead captured from ${lead.source}`
        : "Lead created",
    },
  ].filter(Boolean) as { at: string; text: string }[];

  if (events.length === 0) {
    return (
      <p className="py-8 text-center text-[13px] text-muted-foreground">
        No recorded activity yet.
      </p>
    );
  }

  return (
    <DetailSection title="Recent timeline">
      <ol className="space-y-3 p-3">
        {events.map((e, i) => (
          <li key={i} className="relative border-l border-border pl-4">
            <span
              aria-hidden
              className="absolute -left-[3.5px] top-1.5 size-1.5 rounded-full bg-brand-500"
            />
            <p className="text-[12.5px] text-foreground">{e.text}</p>
            <p className="text-[11px] text-muted-foreground">{formatDate(e.at)}</p>
          </li>
        ))}
      </ol>
      <p className="flex items-center gap-1.5 border-t border-border px-3 py-2 text-[11.5px] text-muted-foreground">
        <Activity className="size-3.5 shrink-0" />
        Calls, emails and meetings are recorded in the Interaction Timeline.
      </p>
    </DetailSection>
  );
}
