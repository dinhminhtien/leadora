import { Suspense } from "react";
import { FollowUpTaskListScreen } from "@/features/follow_up_task/screens/FollowUpTaskListScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function FollowUpTasksPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading tasks..." />}>
      <FollowUpTaskListScreen />
    </Suspense>
  );
}
