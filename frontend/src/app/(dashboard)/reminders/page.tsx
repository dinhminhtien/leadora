import { Suspense } from "react";
import { ReminderListScreen } from "@/features/reminder/screens/ReminderListScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function RemindersPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading reminders..." />}>
      <ReminderListScreen />
    </Suspense>
  );
}
