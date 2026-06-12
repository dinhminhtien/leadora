import { ModulePlaceholder } from "@/shared/components/ModulePlaceholder";

export function ResetPasswordScreen() {
  return (
    <ModulePlaceholder
      title="Reset Password"
      description="Complete password reset after receiving a recovery token."
      cardTitle="Secure reset"
      cardDescription="Token handling and validation can be added here without changing the route structure."
    />
  );
}
