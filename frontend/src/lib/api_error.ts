import type { AxiosError } from "axios";

type ApiErrorBody = {
  success?: boolean;
  message?: string;
  errorCode?: string;
  details?: string;
};

export function getApiErrorMessage(
  error: unknown,
  fallback = "Something went wrong. Please try again."
): string {
  if (!error) return fallback;
  const axiosError = error as AxiosError<ApiErrorBody>;
  const body = axiosError?.response?.data;
  if (body?.message) return body.message;
  return fallback;
}
