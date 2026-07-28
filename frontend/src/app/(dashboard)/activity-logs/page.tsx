import { Suspense } from "react";
import { ActivityLogScreen } from "@/features/activity_log/screens/ActivityLogScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function ActivityLogsPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading activity logs..." />}>
      <ActivityLogScreen />
    </Suspense>
  );
}
