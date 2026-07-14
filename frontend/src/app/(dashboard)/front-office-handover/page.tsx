import { Suspense } from "react";
import { FrontOfficeHandoverScreen } from "@/features/front_office_handover/screens/FrontOfficeHandoverScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function FrontOfficeHandoverPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading handovers..." />}>
      <FrontOfficeHandoverScreen />
    </Suspense>
  );
}
