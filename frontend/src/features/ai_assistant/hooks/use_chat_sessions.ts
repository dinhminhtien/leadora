"use client";

import { useCallback, useRef, useState } from "react";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import type { ApiResponse } from "@/services/api_client";
import {
  chatAssistantService,
  type ChatMessage,
  type UploadProgress,
} from "@/services/chat_assistant_service";

const SESSIONS_KEY = ["chat-sessions"] as const;
const DOCUMENTS_KEY = ["chat-documents"] as const;

export function useChatSessions() {
  return useQuery({
    queryKey: SESSIONS_KEY,
    queryFn: () => chatAssistantService.getSessions(),
  });
}

export function useChatMessages(sessionId: string | null) {
  return useQuery({
    queryKey: ["chat-messages", sessionId],
    queryFn: () => chatAssistantService.getMessages(sessionId as string),
    enabled: !!sessionId,
  });
}

export function useCreateChatSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (title?: string) => chatAssistantService.createSession(title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
  });
}

export function useSendChatMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      content,
    }: {
      sessionId: string;
      content: string;
    }) => chatAssistantService.sendMessage(sessionId, content),
    onSuccess: (res, variables) => {
      // Write the new turn straight into the messages cache. Without this there is a gap
      // between clearing the optimistic "pending" bubble and the invalidated refetch landing,
      // during which the feed briefly renders empty (the "sent question disappears" bug).
      const key = ["chat-messages", variables.sessionId] as const;
      const um = res.data?.userMessage;
      const am = res.data?.assistantMessage;
      queryClient.setQueryData<ApiResponse<ChatMessage[]>>(key, (old) => {
        const list = old?.data ? [...old.data] : [];
        const seen = new Set(list.map((m) => m.messageId));
        for (const m of [um, am]) {
          if (m && !seen.has(m.messageId)) list.push(m);
        }
        return old
          ? { ...old, data: list }
          : ({ success: true, data: list } as ApiResponse<ChatMessage[]>);
      });
      // Reconcile in the background (keeps the cache above as the shown data meanwhile) and
      // refresh the session list so the auto-generated title / ordering updates.
      queryClient.invalidateQueries({ queryKey: key });
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
  });
}

/**
 * Send a message and watch the reply appear as it is written.
 *
 * The assistant spends most of a turn writing rather than deciding, so waiting for the last word
 * before showing the first turns a few seconds of work into a few seconds of blank screen. This
 * exposes the partial text as it arrives; the caller renders `streamingText` under the feed until
 * the finished message lands in the cache and takes its place.
 *
 * The reply is only persisted server-side when the answer completes, so an interrupted turn
 * leaves no half-written message behind — which is also why the partial text is kept in component
 * state rather than written into the query cache as it grows.
 */
export function useStreamChatMessage() {
  const queryClient = useQueryClient();
  const [streamingText, setStreamingText] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const mutation = useMutation({
    mutationFn: async ({
      sessionId,
      content,
    }: {
      sessionId: string;
      content: string;
    }) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      setStreamingText("");
      setIsStreaming(true);

      let accumulated = "";
      return chatAssistantService.streamMessage(
        sessionId,
        content,
        (fragment) => {
          accumulated += fragment;
          setStreamingText(accumulated);
        },
        (userMessage) => {
          // Show the question the moment the server has recorded it, without waiting for the
          // answer — otherwise the input clears and nothing appears for a second or more.
          appendToCache(queryClient, sessionId, [userMessage]);
        },
        controller.signal,
      );
    },
    onSuccess: (res, variables) => {
      appendToCache(queryClient, variables.sessionId, [
        res.userMessage,
        res.assistantMessage,
      ]);
      const key = ["chat-messages", variables.sessionId] as const;
      queryClient.invalidateQueries({ queryKey: key });
      // Refresh the session list so the auto-generated title and ordering update.
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
    onSettled: () => {
      setIsStreaming(false);
      setStreamingText("");
      abortRef.current = null;
    },
  });

  const stop = useCallback(() => abortRef.current?.abort(), []);

  return { ...mutation, streamingText, isStreaming, stop };
}

/** Adds messages to the cached feed, ignoring ones already present. */
function appendToCache(
  queryClient: ReturnType<typeof useQueryClient>,
  sessionId: string,
  messages: (ChatMessage | undefined)[],
) {
  const key = ["chat-messages", sessionId] as const;
  queryClient.setQueryData<ApiResponse<ChatMessage[]>>(key, (old) => {
    const list = old?.data ? [...old.data] : [];
    const seen = new Set(list.map((m) => m.messageId));
    for (const message of messages) {
      if (message && !seen.has(message.messageId)) list.push(message);
    }
    return old
      ? { ...old, data: list }
      : ({ success: true, data: list } as ApiResponse<ChatMessage[]>);
  });
}

export function useRenameChatSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, title }: { sessionId: string; title: string }) =>
      chatAssistantService.renameSession(sessionId, title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
  });
}

export function useDeleteChatSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      chatAssistantService.deleteSession(sessionId),
    onSuccess: (_data, sessionId) => {
      // Drop the deleted session's cached messages so they can't be re-rendered
      // if the selection briefly points back at it before the list refetches.
      queryClient.removeQueries({ queryKey: ["chat-messages", sessionId] });
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
  });
}

// ── RAG company documents ───────────────────────────────────────────────────
export function useCompanyDocuments(enabled: boolean = true) {
  return useQuery({
    queryKey: DOCUMENTS_KEY,
    queryFn: () => chatAssistantService.getDocuments(),
    // The documents endpoint is Manager-only; other roles must not fire the request
    // at all (it would 403 and keep retrying), so callers gate it by role.
    enabled,
    // Ingestion is async on the backend: a freshly uploaded doc starts at chunkCount 0
    // ("processing"). Poll every 4s while any doc is still processing so the row flips to
    // "ready" (or disappears on failure) without the user having to reopen the panel.
    refetchInterval: (query) => {
      const docs = query.state.data?.data ?? [];
      const stillProcessing = docs.some(
        (d) => d.processing || (d.chunkCount ?? 0) === 0,
      );
      return stillProcessing ? 4000 : false;
    },
  });
}

export function useUploadDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      file,
      title,
      onProgress,
    }: {
      file: File;
      title?: string;
      onProgress?: (p: UploadProgress) => void;
    }) => chatAssistantService.uploadDocument(file, title, onProgress),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DOCUMENTS_KEY });
    },
  });
}

export function useDeleteDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (documentId: string) =>
      chatAssistantService.deleteDocument(documentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DOCUMENTS_KEY });
    },
  });
}
