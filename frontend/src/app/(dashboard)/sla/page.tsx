import { Suspense } from "react";
import { SlaManagementScreen } from "@/features/sla/screens/SlaManagementScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function SlaPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading SLA data..." />}>
      <SlaManagementScreen />
    </Suspense>
  );
}
