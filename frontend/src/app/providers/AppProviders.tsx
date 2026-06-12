"use client";

import { AuthProvider } from "@/app/providers/AuthProvider";
import { QueryProvider } from "@/app/providers/QueryProvider";

type AppProvidersProps = {
  children: React.ReactNode;
};

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <QueryProvider>
      <AuthProvider>{children}</AuthProvider>
    </QueryProvider>
  );
}
