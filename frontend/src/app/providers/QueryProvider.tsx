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
            staleTime: 45_000,
            gcTime: 10 * 60 * 1000,
          },
        },
      }),
  );

  useEffect(() => {
    if (typeof window === "undefined") return;

    const handleInvalidate = (event?: Event | MessageEvent) => {
      let entity: string | undefined;
      if (event && "data" in event && typeof event.data === "object" && event.data !== null) {
        entity = (event.data as { entity?: string }).entity;
      } else if (event && "detail" in event && typeof event.detail === "object" && event.detail !== null) {
        entity = (event.detail as { entity?: string }).entity;
      }

      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
      // Any write anywhere (this tab or another) may have triggered a notification for
      // the current user — refresh the bell badge/list instead of waiting for the 30s poll.
      queryClient.invalidateQueries({ queryKey: ["notifications"] });

      if (entity && entity !== "general") {
        queryClient.invalidateQueries({ queryKey: [entity] });
      } else {
        queryClient.invalidateQueries({ queryKey: ["leads"] });
        queryClient.invalidateQueries({ queryKey: ["tasks"] });
        queryClient.invalidateQueries({ queryKey: ["deals"] });
        queryClient.invalidateQueries({ queryKey: ["quotations"] });
        queryClient.invalidateQueries({ queryKey: ["interactions"] });
        queryClient.invalidateQueries({ queryKey: ["customers"] });
      }
    };

    // 1. Listen for cross-tab messages
    let channel: BroadcastChannel | null = null;
    try {
      channel = new BroadcastChannel("leadora-channel");
      channel.addEventListener("message", handleInvalidate as EventListener);
    } catch (e) {
      console.warn("BroadcastChannel not supported", e);
    }

    // 2. Listen for local mutations
    window.addEventListener("leadora-mutate", handleInvalidate as EventListener);

    return () => {
      if (channel) {
        channel.removeEventListener("message", handleInvalidate as EventListener);
        channel.close();
      }
      window.removeEventListener("leadora-mutate", handleInvalidate as EventListener);
    };
  }, [queryClient]);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
