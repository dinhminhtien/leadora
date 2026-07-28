import { Suspense } from "react";

import { TaskWorkspaceScreen } from "@/features/follow_up_task/screens/TaskWorkspaceScreen";
import { ListSkeleton } from "@/components/ui/skeletons";

/**
 * Follow-up tasks — the same single workspace as `/manage-follow-up-tasks`.
 *
 * Both paths resolve to `TaskWorkspaceScreen`: there is exactly **one** task
 * module. This route is kept because notification deep-links point at it
 * (`/follow-up-tasks?highlight={taskId}`, see `notification-center.tsx`), and
 * breaking those links would strand every existing task notification.
 */
export default function FollowUpTasksPage() {
  return (
    <Suspense fallback={<ListSkeleton count={8} />}>
      <TaskWorkspaceScreen />
    </Suspense>
  );
}
