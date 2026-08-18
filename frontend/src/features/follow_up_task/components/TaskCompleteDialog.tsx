"use client";

/**
 * Complete-task dialog — Website UI Blueprint §10.14.5 / §3.16.
 *
 * Completing a task is the single most frequent write in the product, and the
 * server makes it conditional: `PATCH /tasks/{id}/resolve` rejects a blank
 * `resultNote` with `TASK_COMPLETION_NOTE_REQUIRED`. Surfacing that requirement
 * *before* the request is what turns a refused call into a two-second
 * interaction.
 *
 * Shared deliberately: the task list, the task detail drawer, the board and the
 * calendar all complete tasks, and all four must collect the same note and
 * report the same errors. One dialog, four call sites.
 */

import * as React from "react";
import type { UseMutationResult } from "@tanstack/react-query";

import { Button } from "@/components/ui/Button";
import { BlockedHint } from "@/components/ui/guarded-action";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { toast } from "@/stores/toast_store";
import { resolveApiError } from "@/shared/design/error-messages";
import type { Task } from "@/services/follow_up_task_service";

const MAX_LENGTH = 1000;

/** Quick phrases — most completions are one of a handful of outcomes. */
const SUGGESTIONS = [
  "Call completed — customer will confirm",
  "Email sent",
  "Meeting held",
  "No answer — will retry",
];

type TaskCompleteDialogProps = {
  task: Task | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted?: (task: Task) => void;
  /** The shared `useResolveTask()` mutation, owned by the calling screen. */
  resolveTask: UseMutationResult<
    unknown,
    unknown,
    { taskId: string; resultNote: string },
    unknown
  >;
};

export function TaskCompleteDialog({
  open,
  onOpenChange,
  ...rest
}: TaskCompleteDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/*
        Body in its own component: Radix unmounts it on close, so the note and
        error state reset without a cascading reset-in-effect.
      */}
      <TaskCompleteBody onOpenChange={onOpenChange} {...rest} />
    </Dialog>
  );
}

function TaskCompleteBody({
  task,
  onOpenChange,
  onCompleted,
  resolveTask,
}: Omit<TaskCompleteDialogProps, "open">) {
  const [note, setNote] = React.useState("");
  const [touched, setTouched] = React.useState(false);
  const [serverError, setServerError] = React.useState<string | null>(null);

  const missing = note.trim().length === 0;

  const submit = async () => {
    if (!task) return;
    if (missing) {
      setTouched(true);
      return;
    }
    setServerError(null);
    try {
      await resolveTask.mutateAsync({
        taskId: task.taskId,
        resultNote: note.trim(),
      });
      toast.success("Task completed");
      onCompleted?.(task);
      onOpenChange(false);
    } catch (err) {
      // Keep the dialog open so the note the user typed is not lost.
      setServerError(resolveApiError(err).message);
    }
  };

  return (
    <>
      <DialogContent size="md" dismissible={!resolveTask.isPending}>
        <DialogHeader>
          <DialogTitle>Complete this task?</DialogTitle>
          <DialogDescription>
            {task?.title
              ? `“${task.title}” will be marked completed.`
              : "The task will be marked completed."}{" "}
            Linked reminders are cancelled automatically.
          </DialogDescription>
        </DialogHeader>

        <DialogBody className="space-y-3">
          <div className="space-y-1.5">
            <label
              htmlFor="task-result-note"
              className="flex items-center justify-between text-[12px] font-medium text-foreground"
            >
              <span>
                What happened? <span className="text-danger">*</span>
              </span>
              <span className="numeric text-[11px] font-normal text-muted-foreground">
                {note.length}/{MAX_LENGTH}
              </span>
            </label>
            <textarea
              id="task-result-note"
              autoFocus
              rows={4}
              value={note}
              maxLength={MAX_LENGTH}
              onChange={(e) => setNote(e.target.value)}
              onBlur={() => setTouched(true)}
              placeholder="Outcome, next step, anything the next person needs to know…"
              aria-required="true"
              aria-invalid={touched && missing ? true : undefined}
              className={[
                "w-full resize-y rounded-md border bg-surface px-3 py-2 text-[13px] text-foreground",
                "placeholder:text-muted-foreground/70 focus:outline-none focus:ring-2",
                touched && missing
                  ? "border-danger focus:border-danger focus:ring-danger/30"
                  : "border-input focus:border-brand-500 focus:ring-brand-500/30",
              ].join(" ")}
            />
            {touched && missing && (
              <p className="text-[12px] text-danger">
                A completion note is required.
              </p>
            )}
          </div>

          <div className="flex flex-wrap gap-1.5">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setNote(s)}
                className="rounded-pill border border-border bg-surface px-2.5 py-1 text-[11.5px] text-muted-foreground transition-colors hover:border-brand-300 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
              >
                {s}
              </button>
            ))}
          </div>

          {serverError && (
            <p
              role="alert"
              className="rounded-md border border-danger/40 bg-danger-bg/50 px-3 py-2 text-[12.5px] text-danger"
            >
              {serverError}
            </p>
          )}
        </DialogBody>

        <DialogFooter className="flex-wrap">
          {/* BR-16 stated up front rather than only after a failed click. The
              button stays enabled on purpose: clicking it moves focus to the
              note field, which is more useful than a dead control. */}
          {missing && (
            <BlockedHint
              className="mr-auto w-full sm:w-auto"
              reason="A completion note is required before a task can be closed (BR-16)."
            />
          )}
          <Button
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={resolveTask.isPending}
          >
            Cancel
          </Button>
          <Button
            variant="success"
            onClick={submit}
            isLoading={resolveTask.isPending}
          >
            Mark completed
          </Button>
        </DialogFooter>
      </DialogContent>
    </>
  );
}
