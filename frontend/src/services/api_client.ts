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

// Inject Supabase access token as Bearer token on every request
apiClient.interceptors.request.use(async (config) => {
  if (typeof window !== "undefined") {
    const supabase = createSupabaseBrowserClient();
    const {
      data: { session },
    } = await supabase.auth.getSession();

    if (session?.access_token) {
      config.headers.Authorization = `Bearer ${session.access_token}`;
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    if (
      error.response?.status === 401 &&
      typeof window !== "undefined" &&
      window.location.pathname !== "/login"
    ) {
      window.location.assign("/login");
    }

    return Promise.reject(error);
  },
);
