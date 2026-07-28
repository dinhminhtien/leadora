"use client";

/**
 * Reminder detail drawer — shared `RecordDetailDrawer` (§2.11).
 *
 * **`CANCELLED` is explained, not just displayed.** A reminder is cancelled
 * automatically when the task it was attached to is resolved
 * (`ResolveTaskUseCase` cancels every PENDING/OVERDUE reminder on that task).
 * Without saying so, a rep sees a reminder they never cancelled sitting in a
 * cancelled state and reasonably assumes something broke.
 *
 * Dismiss and escalate are offered only where the server would accept them: a
 * DONE or CANCELLED reminder takes neither.
 */

import * as React from "react";
import { AlarmClock, CalendarDays, Link2, StickyNote, User } from "lucide-react";

import {
  RecordDetailDrawer,
  formatDetailDateTime,
  type DetailActionSpec,
} from "@/components/ui/record-drawer";
import { PriorityChip } from "@/components/ui/priority-flag";
import type { Reminder } from "@/services/reminder_service";

export function ReminderDetailDrawer({
  reminder,
  onOpenChange,
  actions = [],
}: {
  reminder: Reminder | null;
  onOpenChange: (open: boolean) => void;
  actions?: DetailActionSpec[];
}) {
  if (!reminder) {
    return <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" sections={[]} />;
  }

  const status = (reminder.status ?? "").toUpperCase();

  return (
    <RecordDetailDrawer
      open
      onOpenChange={onOpenChange}
      size="md"
      avatarIcon={AlarmClock}
      title={reminder.title}
      subtitle={
        reminder.relatedEntity
          ? { icon: Link2, text: `Linked to ${reminder.relatedEntity.toLowerCase()}` }
          : undefined
      }
      status={{ domain: "reminder", value: reminder.status }}
      badges={<PriorityChip size="sm" value={reminder.priority} />}
      recordId={reminder.reminderId}
      actions={actions}
      notice={
        status === "CANCELLED"
          ? {
              tone: "info",
              text: "Auto-cancelled — the task this reminder was attached to has been completed.",
            }
          : status === "OVERDUE"
            ? {
                tone: "warning",
                text: "This reminder is past its time. Managers have been notified.",
              }
            : undefined
      }
      sections={[
        {
          title: "Schedule",
          rows: [
            {
              label: "Remind at",
              icon: CalendarDays,
              value: formatDetailDateTime(reminder.remindAt),
            },
            { label: "Priority", value: <PriorityChip size="sm" value={reminder.priority} /> },
          ],
        },
        {
          title: "Details",
          rows: [
            {
              label: "Description",
              icon: StickyNote,
              value: reminder.description,
              block: true,
            },
            {
              label: "Related",
              icon: Link2,
              value: reminder.relatedEntity ? (
                <span>
                  {reminder.relatedEntity}
                  {reminder.relatedId && (
                    <span className="numeric ml-1 text-muted-foreground">
                      {reminder.relatedId.slice(0, 8).toUpperCase()}
                    </span>
                  )}
                </span>
              ) : null,
            },
          ],
        },
        {
          title: "Ownership",
          rows: [
            { label: "Assigned to", icon: User, value: reminder.assignedUserName },
            { label: "Created by", value: reminder.createdByName },
            { label: "Created", value: formatDetailDateTime(reminder.createdAt) },
          ],
        },
      ]}
    />
  );
}
