import { Suspense } from "react";
import { ContractListScreen } from "@/features/contract/screens/ContractListScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function DashboardContractsPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading contracts..." />}>
      <ContractListScreen />
    </Suspense>
  );
}
