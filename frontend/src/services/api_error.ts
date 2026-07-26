import { AxiosError } from "axios";

import type { ApiErrorResponse } from "@/services/api_client";

/**
 * Turns an API failure into something a user can act on.
 *
 * The response interceptor in `api_client.ts` rejects with the raw `AxiosError`, so
 * screens that render `err.message` show "Request failed with status code 409" — which
 * tells the user nothing. The backend already sends a business `errorCode` plus a
 * human-readable `message` in `ApiResponse`; this reads those instead.
 */

export type ParsedApiError = {
  /** Business error code from `ApiResponse.errorCode`, when the backend sent one. */
  errorCode?: string;
  /** Safe to render. Falls back to a status-appropriate sentence, never a raw axios string. */
  message: string;
  status?: number;
  /** Per-field validation messages, when the backend sent them. */
  fieldErrors?: Record<string, string | string[]>;
};

/** Status-based fallbacks for when the backend sent no usable message. */
function fallbackFor(status?: number): string {
  switch (status) {
    case 400:
      return "Some of the details are invalid. Please review and try again.";
    case 401:
      return "Your session has expired. Please sign in again.";
    case 403:
      return "You do not have permission to do this.";
    case 404:
      return "That record no longer exists.";
    case 409:
      return "This conflicts with the current state of the record. Please refresh and try again.";
    case 422:
      return "This action is not allowed in the record's current state.";
    default:
      return status && status >= 500
        ? "The server ran into a problem. Please try again shortly."
        : "Something went wrong. Please try again.";
  }
}

export function parseApiError(error: unknown): ParsedApiError {
  if (error instanceof AxiosError) {
    const status = error.response?.status;
    const body = error.response?.data as ApiErrorResponse | undefined;

    // A network failure has no response at all — do not blame the user's input for it.
    if (!error.response) {
      return {
        status,
        message: "Cannot reach the server. Check your connection and try again.",
      };
    }

    const backendMessage =
      typeof body?.message === "string" && body.message.trim().length > 0
        ? body.message.trim()
        : undefined;

    return {
      errorCode: body?.errorCode ?? body?.code,
      message: backendMessage ?? fallbackFor(status),
      status,
      fieldErrors: body?.errors,
    };
  }

  if (error instanceof Error && error.message) {
    return { message: error.message };
  }

  return { message: fallbackFor(undefined) };
}

/** Shorthand for the common `catch (e) { setError(...) }` case. */
export function apiErrorMessage(error: unknown): string {
  return parseApiError(error).message;
}

/** Every refusal reason RoomConfirmationGate can return, in backend order of checking. */
export const ROOM_GATE_ERROR_CODES = [
  "ROOM_NOT_REQUESTED",
  "ROOM_PENDING_CONFIRMATION",
  "ROOM_REJECTED",
  "ROOM_CONFIRMATION_STALE",
  "ROOM_HOLD_EXPIRED",
] as const;

/**
 * True when a failure means "the Reservation team has not cleared these rooms" — the
 * screens use this to point the user at the room request instead of showing a bare error.
 */
export function isRoomGateError(error: unknown): boolean {
  const code = parseApiError(error).errorCode;
  return !!code && (ROOM_GATE_ERROR_CODES as readonly string[]).includes(code);
}
