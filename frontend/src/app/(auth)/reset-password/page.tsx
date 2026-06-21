import { Suspense } from "react";
import { ResetPasswordScreen } from "@/features/auth/screens/ResetPasswordScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading reset screen..." />}>
      <ResetPasswordScreen />
    </Suspense>
  );
}
