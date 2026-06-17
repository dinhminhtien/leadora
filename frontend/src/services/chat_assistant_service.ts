import { apiClient, type ApiResponse } from "@/services/api_client";

export type ChatSessionStatus = "ACTIVE" | "DELETED";
export type ChatRole = "USER" | "ASSISTANT";

export type ChatSession = {
  sessionId: string;
  title?: string;
  status?: ChatSessionStatus;
  createdAt?: string;
  updatedAt?: string;
};

export type ChatMessage = {
  messageId: string;
  sessionId?: string;
  role: ChatRole;
  content: string;
  intentMatched?: string | null;
  createdAt?: string;
};

export type SendMessageResult = {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  intent?: string;
  blocked: boolean;
};

export type CompanyDocument = {
  documentId: string;
  title: string;
  fileName?: string;
  contentType?: string;
  chunkCount: number;
  createdAt?: string;
  uploadedById?: string;
  uploadedByName?: string;
};

const SESSIONS_ENDPOINT = "/chat/sessions";
const DOCUMENTS_ENDPOINT = "/chat/documents";

export const chatAssistantService = {
  async createSession(title?: string) {
    const { data } = await apiClient.post<ApiResponse<ChatSession>>(
      SESSIONS_ENDPOINT,
      { title },
    );
    return data;
  },

  async getSessions() {
    const { data } =
      await apiClient.get<ApiResponse<ChatSession[]>>(SESSIONS_ENDPOINT);
    return data;
  },

  async getMessages(sessionId: string) {
    const { data } = await apiClient.get<ApiResponse<ChatMessage[]>>(
      `${SESSIONS_ENDPOINT}/${sessionId}/messages`,
    );
    return data;
  },

  async sendMessage(sessionId: string, content: string) {
    const { data } = await apiClient.post<ApiResponse<SendMessageResult>>(
      `${SESSIONS_ENDPOINT}/${sessionId}/messages`,
      { content },
    );
    return data;
  },

  async deleteSession(sessionId: string) {
    const { data } = await apiClient.delete<ApiResponse<null>>(
      `${SESSIONS_ENDPOINT}/${sessionId}`,
    );
    return data;
  },

  // ── RAG company documents ───────────────────────────────────────────────
  async getDocuments() {
    const { data } =
      await apiClient.get<ApiResponse<CompanyDocument[]>>(DOCUMENTS_ENDPOINT);
    return data;
  },

  async uploadDocument(file: File, title?: string) {
    const form = new FormData();
    form.append("file", file);
    if (title) form.append("title", title);
    const { data } = await apiClient.post<ApiResponse<CompanyDocument>>(
      DOCUMENTS_ENDPOINT,
      form,
      // Let the browser set the multipart boundary instead of the JSON default.
      { headers: { "Content-Type": undefined } },
    );
    return data;
  },

  async deleteDocument(documentId: string) {
    const { data } = await apiClient.delete<ApiResponse<null>>(
      `${DOCUMENTS_ENDPOINT}/${documentId}`,
    );
    return data;
  },
};
