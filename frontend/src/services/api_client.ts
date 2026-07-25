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
  page: number | {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};

export type ApiErrorResponse = {
  status?: number;
  code?: string;
  message: string;
  errorCode?: string;
  details?: string;
  errors?: Record<string, string | string[]>;
  path?: string;
  timestamp?: string;
};

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8085/api/v1";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    Accept: "application/json",
    "Content-Type": "application/json",
  },
});

/**
 * The access token, from wherever it currently lives.
 *
 * Exported because not every call can go through axios: the streaming chat endpoint reads its
 * response body incrementally, which needs `fetch`. Sharing this keeps the two paths from
 * drifting apart — a second copy of the lookup order is a bug waiting for the day one of them
 * changes.
 */
export async function resolveAccessToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;

  const stored = localStorage.getItem("accessToken");
  if (stored) return stored;

  const cookie = document.cookie.match(/(^|;)\s*accessToken\s*=\s*([^;]+)/);
  if (cookie) return cookie[2];

  const supabase = createSupabaseBrowserClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  return session?.access_token ?? null;
}

/** Authorization header for `fetch` calls, empty when there is no token to send. */
export async function authHeaders(): Promise<Record<string, string>> {
  const token = await resolveAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// Inject access token as Bearer token on every request
apiClient.interceptors.request.use(async (config) => {
  if (typeof window !== "undefined") {
    // Check if it's a public auth endpoint
    const isPublicAuth = config.url && (
      config.url.endsWith("/auth/login") ||
      config.url.endsWith("/auth/forgot-password") ||
      config.url.endsWith("/auth/reset-password")
    );

    if (!isPublicAuth) {
      const token = await resolveAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
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
      try {
        const { supabaseAuthService } = require("@/services/supabase_auth_service");
        supabaseAuthService.clearLocalSession();
        supabaseAuthService.signOut();
      } catch (e) {
        console.warn("Failed to clear session on 401", e);
      }
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }

    return Promise.reject(error);
  },
);
