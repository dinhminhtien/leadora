"use client";

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
export function useCompanyDocuments() {
  return useQuery({
    queryKey: DOCUMENTS_KEY,
    queryFn: () => chatAssistantService.getDocuments(),
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
