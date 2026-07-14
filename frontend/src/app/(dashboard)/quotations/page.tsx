import { Suspense } from "react";
import { QuotationListScreen } from "@/features/quotation/screens/QuotationListScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function QuotationsPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading quotations..." />}>
      <QuotationListScreen />
    </Suspense>
  );
}
