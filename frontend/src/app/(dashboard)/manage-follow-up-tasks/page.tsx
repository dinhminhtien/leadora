import { Suspense } from "react";

import { TaskWorkspaceScreen } from "@/features/follow_up_task/screens/TaskWorkspaceScreen";
import { ListSkeleton } from "@/components/ui/skeletons";

/**
 * The task **workspace** — Website UI Blueprint §10.14.
 *
 * This is the surface the sidebar links to: eight views over one dataset, for
 * triage, bulk actions and team oversight. Creating or editing a single task
 * opens `/follow-up-tasks`, which owns the create/edit/resign drawers, so each
 * form has exactly one implementation.
 *
 * `useSearchParams` needs a Suspense boundary in the App Router.
 */
export default function ManageFollowUpTasksPage() {
  return (
    <Suspense fallback={<ListSkeleton count={8} />}>
      <TaskWorkspaceScreen />
    </Suspense>
  );
}
