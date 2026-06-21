import axios, { type AxiosError } from "axios";

import { createSupabaseBrowserClient } from "@/services/supabase/client";

export type ApiResponse<T> = {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
};

export type ApiErrorResponse = {
  status?: number;
  code?: string;
  message: string;
  errors?: Record<string, string | string[]>;
  path?: string;
  timestamp?: string;
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8085/api/v1";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    Accept: "application/json",
    "Content-Type": "application/json",
  },
});

// Inject access token as Bearer token on every request
apiClient.interceptors.request.use(async (config) => {
  if (typeof window !== "undefined") {
    // Check if it's a public auth endpoint
    const isPublicAuth = config.url && (
      config.url.endsWith("/auth/login") ||
      config.url.endsWith("/auth/forgot-password") ||
      config.url.endsWith("/auth/reset-password") ||
      config.url.endsWith("/auth/logout")
    );

    if (!isPublicAuth) {
      const localToken = localStorage.getItem("accessToken");
      if (localToken) {
        config.headers.Authorization = `Bearer ${localToken}`;
      } else {
        const supabase = createSupabaseBrowserClient();
        const {
          data: { session },
        } = await supabase.auth.getSession();

        if (session?.access_token) {
          config.headers.Authorization = `Bearer ${session.access_token}`;
        }
      }
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    // If it's a mutating request, broadcast invalidation to other tabs and dispatch locally
    const method = response.config.method?.toLowerCase();
    if (
      method &&
      ["post", "put", "patch", "delete"].includes(method) &&
      typeof window !== "undefined"
    ) {
      try {
        const channel = new BroadcastChannel("leadora-channel");
        channel.postMessage("invalidate-dashboard");
        channel.close();
      } catch (e) {
        console.warn("Failed to broadcast change", e);
      }
      try {
        window.dispatchEvent(new Event("leadora-mutate"));
      } catch (e) {
        console.warn("Failed to dispatch local mutation event", e);
      }
    }
    return response;
  },
  (error: AxiosError<ApiErrorResponse>) => {
    if (
      error.response?.status === 401 &&
      typeof window !== "undefined"
    ) {
      localStorage.removeItem("accessToken"); // Clear the invalid token
      try {
        const { createSupabaseBrowserClient } = require("@/services/supabase/client");
        const supabase = createSupabaseBrowserClient();
        supabase.auth.signOut();
      } catch (e) {
        console.warn("Failed to clear Supabase session", e);
      }
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }

    return Promise.reject(error);
  },
);
