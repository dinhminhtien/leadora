"use client";

/**
 * Task workspace — the flagship detail surface (Blueprint §10.14).
 *
 * **Why a full page and not a bigger drawer.** The task drawer is a *peek*: it
 * keeps the list's filters, page and scroll while a rep glances at a row. A
 * workspace is where they actually work — three panes, a right rail of related
 * records, and enough room for a timeline. Those are different jobs, so this is
 * a route (`/follow-up-tasks/{id}`) and the drawer links into it, exactly the
 * §9.3 split the customer module uses.
 *
 * **Tabs are built from what the API can actually answer.** `GET /tasks/{id}`
 * plus the activity-log and reminder services support Overview, Activity/Audit
 * and Reminders. The brief also asked for Comments, Checklist, Attachments,
 * Time Tracking and AI Suggestions — there is no endpoint behind any of those,
 * and a tab that is permanently empty is worse than no tab: it advertises a
 * feature the product does not have. They are listed in `PLANNED_TABS` below so
 * the gap is visible in code review rather than invisible in the UI.
 *
 * Business rules surfaced here: BR-15 (a task needs type/assignee/due/priority/
 * status and one primary related object), BR-16 (completion requires a note),
 * BR-17 (overdue is computed, never stored), BR-18 (only a manager reassigns),
 * BR-19 (an active record should have a next action).
 */

import * as React from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  AlarmClock,
  ArrowRight,
  Building2,
  CalendarDays,
  CheckCircle2,
  Clock,
  History as HistoryIcon,
  Info,
  Mail,
  Phone,
  User,
  UserCog,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { StatusPill } from "@/components/ui/status-pill";
import { PriorityChip } from "@/components/ui/priority-flag";
import { DetailSkeleton } from "@/components/ui/skeletons";
import { ErrorState, EmptyState } from "@/components/ui/states";
import { BlockedHint, GuardedButton } from "@/components/ui/guarded-action";
import { Timeline, groupByMonth, type TimelineItemSpec } from "@/components/ui/timeline";
import { timelineEventKind } from "@/shared/design/timeline-events";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { isTaskOverdue } from "@/shared/design/status-tokens";
import { useAuthStore } from "@/stores/auth_store";
import { hasFullAccess } from "@/shared/auth/access";
import {
  useTaskDetail,
  useTasks,
} from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { activityLogService, type ActivityLog } from "@/services/activity_log_service";
import { useQuery } from "@tanstack/react-query";
import type { Task } from "@/services/follow_up_task_service";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";

/**
 * Requested in the RC brief but with no backing endpoint. Kept as a named list
 * rather than as empty tabs — see the file header.
 */
const PLANNED_TABS = [
  "Comments",
  "Checklist",
  "Attachments",
  "Time tracking",
  "AI suggestions",
] as const;

type TabId = "overview" | "activity" | "audit";

const TABS: { id: TabId; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "activity", label: "Timeline" },
  { id: "audit", label: "History & Audit" },
];

function fmtDateTime(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/* ------------------------------------------------------------------ *
 * Small presentational pieces
 * ------------------------------------------------------------------ */

function Field({
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
        {value || <span className="text-muted-foreground">—</span>}
      </span>
    </div>
  );
}

function Panel({
  title,
  action,
  children,
}: {
  title: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          {title}
        </h3>
        {action}
      </div>
      <div className="overflow-hidden rounded-lg border border-border bg-surface">
        {children}
      </div>
    </section>
  );
}

/**
 * The one related record a task hangs off (BR-15: exactly one primary object).
 * Renders whichever of lead / customer / deal the task is linked to, with a
 * route into it — the rail exists so a rep never has to leave to get context.
 */
function RelatedRecord({ task }: { task: Task }) {
  if (task.leadId) {
    return (
      <div className="p-3">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Lead</p>
        <Link
          href={ROUTE_PATHS.leadDetail(task.leadId)}
          className="mt-0.5 block truncate text-[13px] font-semibold text-primary hover:underline"
        >
          {task.leadName ?? "Open lead"}
        </Link>
        {task.leadCompanyName && (
          <p className="mt-1 flex items-center gap-1.5 text-[11.5px] text-muted-foreground">
            <Building2 className="size-3" />
            {task.leadCompanyName}
          </p>
        )}
        {task.leadPhone && (
          <a href={`tel:${task.leadPhone}`} className="mt-1 flex items-center gap-1.5 text-[11.5px] text-primary hover:underline">
            <Phone className="size-3" />
            {task.leadPhone}
          </a>
        )}
        {task.leadEmail && (
          <a href={`mailto:${task.leadEmail}`} className="mt-1 flex items-center gap-1.5 truncate text-[11.5px] text-primary hover:underline">
            <Mail className="size-3" />
            {task.leadEmail}
          </a>
        )}
      </div>
    );
  }

  if (task.customerId) {
    return (
      <div className="p-3">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Customer</p>
        <Link
          href={`${ROUTE_PATHS.customerProfiles}/${task.customerId}`}
          className="mt-0.5 block truncate text-[13px] font-semibold text-primary hover:underline"
        >
          {task.customerName ?? "Open customer"}
        </Link>
        {task.customerCompanyName && (
          <p className="mt-1 flex items-center gap-1.5 text-[11.5px] text-muted-foreground">
            <Building2 className="size-3" />
            {task.customerCompanyName}
          </p>
        )}
        {task.customerPhone && (
          <a href={`tel:${task.customerPhone}`} className="mt-1 flex items-center gap-1.5 text-[11.5px] text-primary hover:underline">
            <Phone className="size-3" />
            {task.customerPhone}
          </a>
        )}
        {task.customerEmail && (
          <a href={`mailto:${task.customerEmail}`} className="mt-1 flex items-center gap-1.5 truncate text-[11.5px] text-primary hover:underline">
            <Mail className="size-3" />
            {task.customerEmail}
          </a>
        )}
      </div>
    );
  }

  if (task.dealId) {
    return (
      <div className="p-3">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Deal</p>
        <Link href={ROUTE_PATHS.deals} className="mt-0.5 block truncate text-[13px] font-semibold text-primary hover:underline">
          {task.dealName ?? "Open deal"}
        </Link>
        {task.dealStage && (
          <p className="mt-1 text-[11.5px] text-muted-foreground">Stage: {task.dealStage}</p>
        )}
      </div>
    );
  }

  // BR-15 requires one primary related object; a task without one predates the
  // rule or was created through an older client, and is worth flagging.
  return (
    <div className="p-3">
      <BlockedHint
        tone="warning"
        reason="This task is not linked to a lead, customer or deal. BR-15 expects one primary related object."
      />
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Screen
 * ------------------------------------------------------------------ */

export function TaskDetailWorkspaceScreen({ taskId }: { taskId: string }) {
  const router = useRouter();
  const { user } = useAuthStore();
  const canReassign = hasFullAccess(user);

  const [tab, setTab] = React.useState<TabId>("overview");

  const { data, isLoading, isError, error, refetch } = useTaskDetail(taskId);
  const task = data?.data;

  /**
   * Activity for this task. `entityType`/`entityId` is the only entity filter
   * the audit endpoint offers, which is exactly what the Timeline and Audit
   * tabs need — both read this one query rather than fetching twice.
   */
  const activityQuery = useQuery({
    queryKey: ["activity-log", "TASK", taskId],
    queryFn: () =>
      activityLogService.getList({ entityType: "TASK", entityId: taskId, page: 0, size: 50 }),
    enabled: !!taskId,
  });

  const logs: ActivityLog[] = React.useMemo(() => {
    const payload = activityQuery.data?.data as { content?: ActivityLog[] } | ActivityLog[] | undefined;
    if (!payload) return [];
    return Array.isArray(payload) ? payload : (payload.content ?? []);
  }, [activityQuery.data]);

  /**
   * The rail's "what's next" list: the same owner's other open work, so a rep
   * finishing this task can pick up the next one without going back to the list
   * (BR-19 — an active record should always have a next action in view).
   */
  const upcomingQuery = useTasks(
    task?.assignedUserId
      ? { assignedUserId: task.assignedUserId, status: "OPEN", page: 0, size: 6 }
      : undefined,
  );
  const upcoming: Task[] = React.useMemo(() => {
    const payload = upcomingQuery.data?.data;
    const rows = (payload as { content?: Task[] })?.content ?? [];
    return rows.filter((t) => t.taskId !== taskId).slice(0, 5);
  }, [upcomingQuery.data, taskId]);

  if (isLoading) return <DetailSkeleton />;
  if (isError || !task) {
    return <ErrorState error={error ?? new Error("Task not found.")} onRetry={() => refetch()} />;
  }

  const overdue = isTaskOverdue(task);
  const isClosed = task.status === "COMPLETED" || task.status === "CANCELLED";

  const timelineGroups = groupByMonth(
    logs,
    (l) => new Date(l.createdAt).getTime(),
    (l): TimelineItemSpec => ({
      id: l.id,
      kind: timelineEventKind(l.activityType, "task"),
      title: l.summary,
      timestamp: fmtDateTime(l.createdAt),
      actor: l.actor?.fullName
        ? `${l.actor.fullName}${l.actor.role ? ` · ${l.actor.role}` : ""}`
        : undefined,
    }),
  );

  return (
    <div className="space-y-5 pb-10">
      <PageHeader
        crumbs={[
          ...PAGE_META.followUpTasks.crumbs.slice(0, -1),
          { label: "Follow-up Tasks", href: ROUTE_PATHS.manageFollowUpTasks },
          { label: task.title },
        ]}
        title={task.title}
        meta={
          <>
            <StatusPill size="sm" domain="task" value={task.status} />
            <PriorityChip value={task.priority} />
            {/* BR-17 — overdue is computed from due date + status, never stored,
                so it renders beside the status pill rather than replacing it. */}
            {overdue && (
              <span className="inline-flex items-center gap-1 rounded-pill bg-danger/12 px-2 py-0.5 text-[10px] font-bold text-danger">
                <Clock className="size-3" />
                OVERDUE
              </span>
            )}
            <SlaStatusBadge entityId={task.taskId} entityType="TASK" />
          </>
        }
        actions={
          <>
            <GuardedButton
              variant="secondary"
              leftIcon={<UserCog className="size-4" />}
              reason={
                !canReassign
                  ? "Only a Sales Manager can reassign a follow-up task (BR-18)."
                  : isClosed
                    ? "This task is closed. Reopen it before reassigning."
                    : null
              }
              onClick={() => router.push(`${ROUTE_PATHS.manageFollowUpTasks}?reassign=${task.taskId}`)}
            >
              Reassign
            </GuardedButton>
            <GuardedButton
              variant="primary"
              leftIcon={<CheckCircle2 className="size-4" />}
              reason={
                isClosed
                  ? `This task is already ${task.status.toLowerCase()}.`
                  : null
              }
              // Completion runs through the list's dialog, which enforces BR-16
              // (a completion note is required) — duplicating that form here
              // would mean two places to keep the rule correct.
              onClick={() => router.push(`${ROUTE_PATHS.manageFollowUpTasks}?complete=${task.taskId}`)}
            >
              Complete
            </GuardedButton>
          </>
        }
      />

      {/* Main + rail. Single column below `xl` so the rail stacks under the
          content rather than squeezing both into unreadable widths. */}
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-4">
          <div role="tablist" aria-label="Task sections" className="flex gap-1 border-b border-border">
            {TABS.map((t) => (
              <button
                key={t.id}
                role="tab"
                type="button"
                aria-selected={tab === t.id}
                onClick={() => setTab(t.id)}
                className={cn(
                  "-mb-px border-b-2 px-3 py-2 text-[12.5px] font-semibold transition-colors",
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                  tab === t.id
                    ? "border-brand-500 text-foreground"
                    : "border-transparent text-muted-foreground hover:text-foreground",
                )}
              >
                {t.label}
              </button>
            ))}
          </div>

          {tab === "overview" && (
            <div className="space-y-4">
              {task.description && (
                <Panel title="Description">
                  <p className="whitespace-pre-wrap px-3 py-2.5 text-[12.5px] leading-relaxed text-foreground">
                    {task.description}
                  </p>
                </Panel>
              )}

              <Panel title="Schedule">
                <Field label="Starts" icon={CalendarDays} value={fmtDateTime(task.startAt)} />
                <Field
                  label="Due"
                  icon={Clock}
                  value={
                    <span className={overdue ? "font-semibold text-danger" : undefined}>
                      {fmtDateTime(task.endAt)}
                    </span>
                  }
                />
              </Panel>

              <Panel title="Assignment">
                <Field label="Assignee" icon={User} value={task.assignedUserName} />
                <Field label="Created by" value={task.createdByName} />
                <Field label="Created" value={fmtDateTime(task.createdAt)} />
                <Field label="Last updated" value={fmtDateTime(task.updatedAt)} />
              </Panel>

              <Panel title="Outcome">
                {task.resultNote ? (
                  <p className="whitespace-pre-wrap px-3 py-2.5 text-[12.5px] leading-relaxed text-foreground">
                    {task.resultNote}
                  </p>
                ) : (
                  <div className="px-3 py-2.5">
                    {/* BR-16 stated before the user reaches the dialog. */}
                    <BlockedHint reason="No completion note yet. One is required before this task can be closed (BR-16)." />
                  </div>
                )}
              </Panel>
            </div>
          )}

          {tab === "activity" && (
            <>
              {activityQuery.isLoading ? (
                <DetailSkeleton />
              ) : (
                <Timeline
                  groups={timelineGroups}
                  emptyMessage="No activity recorded against this task yet."
                />
              )}
            </>
          )}

          {tab === "audit" && (
            <Panel title="Audit trail">
              {logs.length === 0 ? (
                <EmptyState
                  variant="initial"
                  title="No audit entries"
                  message="Changes to this task will be recorded here."
                />
              ) : (
                <ul className="divide-y divide-border">
                  {logs.map((l) => (
                    <li key={l.id} className="px-3 py-2.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[10px] font-bold uppercase tracking-wide text-muted-foreground">
                          {l.activityType}
                        </span>
                        <span className="ml-auto text-[11px] text-muted-foreground">
                          {fmtDateTime(l.createdAt)}
                        </span>
                      </div>
                      <p className="mt-0.5 text-[12.5px] text-foreground">{l.summary}</p>
                      <p className="mt-1 text-[11px] text-muted-foreground">
                        {l.actor?.fullName ?? "System"}
                        {l.actor?.role ? ` · ${l.actor.role}` : ""}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          )}
        </div>

        {/* Right rail — context that would otherwise cost a round trip to another
            screen: the record this task is about, and what to do next. */}
        <aside className="min-w-0 space-y-4">
          <Panel title="Related record">
            <RelatedRecord task={task} />
          </Panel>

          <Panel
            title="Next up for this owner"
            action={
              <Link
                href={ROUTE_PATHS.manageFollowUpTasks}
                className="text-[11px] font-medium text-primary hover:underline"
              >
                All tasks
              </Link>
            }
          >
            {upcoming.length === 0 ? (
              <p className="px-3 py-4 text-center text-[12px] text-muted-foreground">
                No other open tasks for {task.assignedUserName ?? "this owner"}.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {upcoming.map((t) => (
                  <li key={t.taskId}>
                    <Link
                      href={`${ROUTE_PATHS.manageFollowUpTasks}/${t.taskId}`}
                      className="flex items-start gap-2 px-3 py-2.5 transition-colors hover:bg-surface-2"
                    >
                      <AlarmClock
                        className={cn(
                          "mt-0.5 size-3.5 shrink-0",
                          isTaskOverdue(t) ? "text-danger" : "text-muted-foreground",
                        )}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[12.5px] font-medium text-foreground">
                          {t.title}
                        </span>
                        <span className="block text-[11px] text-muted-foreground">
                          {fmtDateTime(t.endAt)}
                        </span>
                      </span>
                      <ArrowRight className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Recent activity">
            {logs.length === 0 ? (
              <p className="px-3 py-4 text-center text-[12px] text-muted-foreground">
                Nothing recorded yet.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {logs.slice(0, 4).map((l) => (
                  <li key={l.id} className="px-3 py-2.5">
                    <p className="truncate text-[12px] text-foreground">{l.summary}</p>
                    <p className="mt-0.5 flex items-center gap-1 text-[11px] text-muted-foreground">
                      <HistoryIcon className="size-3" />
                      {fmtDateTime(l.createdAt)}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          {/* Honest about scope: these were specified but have no endpoint, so
              the workspace says so rather than showing five empty tabs. */}
          <Panel title="Not yet available">
            <div className="space-y-1.5 px-3 py-2.5">
              <p className="flex items-start gap-1.5 text-[11.5px] leading-[16px] text-muted-foreground">
                <Info aria-hidden className="mt-px size-3.5 shrink-0" />
                <span>
                  Comments, checklists, attachments, time tracking and AI suggestions
                  are not backed by an API yet, so they are not shown as empty tabs.
                </span>
              </p>
              <ul className="flex flex-wrap gap-1">
                {PLANNED_TABS.map((t) => (
                  <li
                    key={t}
                    className="rounded-pill border border-dashed border-border px-2 py-0.5 text-[10.5px] text-muted-foreground"
                  >
                    {t}
                  </li>
                ))}
              </ul>
            </div>
          </Panel>
        </aside>
      </div>
    </div>
  );
}
