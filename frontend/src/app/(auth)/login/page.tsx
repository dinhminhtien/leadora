import { Suspense } from "react";
import { LoginScreen } from "@/features/auth/screens/LoginScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function LoginPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading auth form..." />}>
      <LoginScreen />
    </Suspense>
  );
}

