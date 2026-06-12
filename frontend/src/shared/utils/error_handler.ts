import { isAxiosError } from "axios";

import type { ApiErrorResponse } from "@/shared/types/api";

export function getErrorMessage(
  error: unknown,
  fallback = "Something went wrong.",
) {
  if (isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.message ?? error.message ?? fallback;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
