"use client";
import { useEffect } from "react";
import { AuthProvider } from "@/app/providers/AuthProvider";
import { QueryProvider } from "@/app/providers/QueryProvider";
import { useUiStore } from "@/stores/ui_store";

type AppProvidersProps = {
  children: React.ReactNode;
};

export function AppProviders({ children }: AppProvidersProps) {
  const { theme } = useUiStore();
  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/v1/health", {
      cache: "no-store",
      signal: controller.signal,
    }).catch(() => {
      // Silently catch errors/abortions as this is a fire-and-forget wake-up call
    });

    return () => {
      controller.abort();
    };
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === "dark") {
      root.classList.add("dark");
      root.classList.remove("light");
    } else {
      root.classList.add("light");
      root.classList.remove("dark");
    }
  }, [theme]);

  return (
    <QueryProvider>
      <AuthProvider>{children}</AuthProvider>
    </QueryProvider>
  );
}

