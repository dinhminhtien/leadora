"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { chatAssistantService } from "@/services/chat_assistant_service";

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
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["chat-messages", variables.sessionId],
      });
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
  });
}

export function useDeleteChatSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      chatAssistantService.deleteSession(sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
  });
}

// ── RAG company documents ───────────────────────────────────────────────────
export function useCompanyDocuments() {
  return useQuery({
    queryKey: DOCUMENTS_KEY,
    queryFn: () => chatAssistantService.getDocuments(),
  });
}

export function useUploadDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, title }: { file: File; title?: string }) =>
      chatAssistantService.uploadDocument(file, title),
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
