"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, useEffect } from "react";

type QueryProviderProps = {
  children: React.ReactNode;
};

export function QueryProvider({ children }: QueryProviderProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: 1,
            staleTime: 30_000,
          },
        },
      }),
  );

  useEffect(() => {
    if (typeof window === "undefined") return;

    const handleInvalidate = () => {
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
      // Any write anywhere (this tab or another) may have triggered a notification for
      // the current user — refresh the bell badge/list instead of waiting for the 30s poll.
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    };

    // 1. Listen for cross-tab messages
    let channel: BroadcastChannel | null = null;
    try {
      channel = new BroadcastChannel("leadora-channel");
      channel.addEventListener("message", handleInvalidate);
    } catch (e) {
      console.warn("BroadcastChannel not supported", e);
    }

    // 2. Listen for local mutations
    window.addEventListener("leadora-mutate", handleInvalidate);

    return () => {
      if (channel) {
        channel.removeEventListener("message", handleInvalidate);
        channel.close();
      }
      window.removeEventListener("leadora-mutate", handleInvalidate);
    };
  }, [queryClient]);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
