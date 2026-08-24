import axios, { type AxiosError } from "axios";

import { createSupabaseBrowserClient } from "@/services/supabase/client";
import { supabaseAuthService } from "@/services/supabase_auth_service";

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

export type PageMeta = {
  /** Zero-based index of the page that came back. */
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  /** True when this is the final page — the signal infinite scroll stops on. */
  last: boolean;
};

/**
 * Normalises the two shapes a Spring `Page` reaches the browser in.
 *
 * Depending on `spring.data.web.pageable.serialization-mode`, the metadata arrives either
 * flat (`{ content, totalPages, last }`) or nested (`{ content, page: { number, size,
 * totalElements, totalPages } }`). Reading the flat fields off a nested payload silently
 * yields `undefined`, which is how several screens ended up believing every result set
 * was exactly one page long. Read paging metadata through here instead of by hand.
 */
export function pageMeta<T>(page?: PageResponse<T> | null): PageMeta {
  const nested = page && typeof page.page === "object" ? page.page : null;

  const number = nested ? nested.number : typeof page?.page === "number" ? page.page : 0;
  const size = nested ? nested.size : (page?.size ?? page?.content?.length ?? 0);
  const totalElements = nested
    ? nested.totalElements
    : (page?.totalElements ?? page?.content?.length ?? 0);
  const totalPages = nested ? nested.totalPages : (page?.totalPages ?? (totalElements === 0 ? 0 : 1));

  // The nested form carries no `last` flag, so derive it. `totalPages === 0` (an empty
  // result) is terminal too, otherwise infinite scroll would page forever on no rows.
  const last = page?.last ?? (totalPages === 0 || number + 1 >= totalPages);

  return { number, size, totalElements, totalPages, last };
}

export type ApiErrorResponse = {
  status?: number;
  code?: string;
  message: string;
  errorCode?: string;
  details?: string;
  errors?: Record<string, string | string[]>;
  path?: string;
  timestamp?: string;
  correlationId?: string;
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

export type MutatedEntity =
  | "leads"
  | "tasks"
  | "deals"
  | "quotations"
  | "customers"
  | "interactions"
  | "reminders"
  | "contracts"
  | "general";

const ROUTE_ENTITY_MAP: Array<{ match: RegExp; entity: MutatedEntity }> = [
  { match: /\/leads(\/|$|\?)/, entity: "leads" },
  { match: /\/tasks(\/|$|\?)/, entity: "tasks" },
  { match: /\/deals(\/|$|\?)/, entity: "deals" },
  { match: /\/quotations(\/|$|\?)/, entity: "quotations" },
  { match: /\/customers(\/|$|\?)/, entity: "customers" },
  { match: /\/interactions(\/|$|\?)/, entity: "interactions" },
  { match: /\/reminders(\/|$|\?)/, entity: "reminders" },
  { match: /\/contracts(\/|$|\?)/, entity: "contracts" },
];

export function resolveEntityFromUrl(url?: string): MutatedEntity {
  if (!url) return "general";
  const found = ROUTE_ENTITY_MAP.find((m) => m.match.test(url));
  return found ? found.entity : "general";
}

apiClient.interceptors.response.use(
  (response) => {
    // If it's a mutating request, broadcast invalidation to other tabs and dispatch locally
    const method = response.config.method?.toLowerCase();
    if (
      method &&
      ["post", "put", "patch", "delete"].includes(method) &&
      typeof window !== "undefined"
    ) {
      const entity = resolveEntityFromUrl(response.config.url);
      const payload = { type: "invalidate", entity };
      try {
        const channel = new BroadcastChannel("leadora-channel");
        channel.postMessage(payload);
        channel.close();
      } catch (e) {
        console.warn("Failed to broadcast change", e);
      }
      try {
        window.dispatchEvent(new CustomEvent("leadora-mutate", { detail: payload }));
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
        supabaseAuthService.clearLocalSession();
        supabaseAuthService.signOut();
      } catch (e) {
        console.warn("Failed to clear session on 401", e);
      }
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }

    // Extract Correlation ID from headers or response payload
    const correlationId =
      error.response?.headers?.["x-correlation-id"] ||
      error.response?.headers?.["X-Correlation-ID"] ||
      error.response?.data?.correlationId;

    if (correlationId) {
      const suffix = ` (Correlation ID: ${correlationId})`;
      if (error.response?.data) {
        const data = error.response.data;
        if (data.message && !data.message.includes("Correlation ID")) {
          data.message = `${data.message}${suffix}`;
        }
        data.correlationId = correlationId;
      }
      if (error.message && !error.message.includes("Correlation ID")) {
        error.message = `${error.message}${suffix}`;
      }
    }

    return Promise.reject(error);
  },
);
