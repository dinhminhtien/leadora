import { apiClient, type ApiResponse } from "@/services/api_client";

export type ChatSession = Record<string, unknown> & {
  id: string;
  title?: string;
};

export type ChatMessage = Record<string, unknown> & {
  id: string;
  content?: string;
  role?: string;
};

export type CreateChatSessionPayload = Record<string, unknown>;

export type SendMessagePayload = {
  content: string;
};

const SESSIONS_ENDPOINT = "/chat/sessions";

export const chatAssistantService = {
  async createSession(payload: CreateChatSessionPayload = {}) {
    const response = await apiClient.post<ApiResponse<ChatSession>>(
      SESSIONS_ENDPOINT,
      payload,
    );
    return response.data;
  },

  async getSessions() {
    const response =
      await apiClient.get<ApiResponse<ChatSession[]>>(SESSIONS_ENDPOINT);
    return response.data;
  },

  async getMessages(sessionId: string) {
    const response = await apiClient.get<ApiResponse<ChatMessage[]>>(
      `${SESSIONS_ENDPOINT}/${sessionId}/messages`,
    );
    return response.data;
  },

  async sendMessage(sessionId: string, payload: SendMessagePayload) {
    const response = await apiClient.post<ApiResponse<ChatMessage>>(
      `${SESSIONS_ENDPOINT}/${sessionId}/messages`,
      payload,
    );
    return response.data;
  },

  async deleteSession(sessionId: string) {
    const response = await apiClient.delete<ApiResponse<null>>(
      `${SESSIONS_ENDPOINT}/${sessionId}`,
    );
    return response.data;
  },
};
