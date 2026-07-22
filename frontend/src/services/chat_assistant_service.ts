import {
  API_BASE_URL,
  apiClient,
  authHeaders,
  type ApiResponse,
} from "@/services/api_client";

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
  /** True while the doc is still being parsed/embedded in the background (chunkCount === 0). */
  processing?: boolean;
  createdAt?: string;
  uploadedById?: string;
  uploadedByName?: string;
};

/** Progress of the browser→server byte transfer for an upload. */
export type UploadProgress = {
  loaded: number;
  total: number;
  /** 0–100, rounded. */
  percent: number;
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

  /**
   * Send a message and receive the reply as it is written.
   *
   * Uses `fetch` rather than `EventSource`, which cannot set an `Authorization` header — it only
   * sends cookies, and this API is Bearer-authenticated. Reading the body as a stream gives the
   * same server-sent-event framing with the header attached.
   *
   * Falls back to the blocking endpoint on any transport failure, so a proxy that buffers
   * responses or a network that blocks event streams degrades to "slower" rather than "broken".
   *
   * @param onToken called with each fragment as it arrives; concatenate in order
   * @param signal  abort to stop reading and let the server drop the turn
   */
  async streamMessage(
    sessionId: string,
    content: string,
    onToken: (fragment: string) => void,
    onStart?: (userMessage: ChatMessage, intent?: string, blocked?: boolean) => void,
    signal?: AbortSignal,
  ): Promise<SendMessageResult> {
    let res: Response;
    try {
      res = await fetch(
        `${API_BASE_URL}${SESSIONS_ENDPOINT}/${sessionId}/messages/stream`,
        {
          method: "POST",
          headers: {
            ...(await authHeaders()),
            "Content-Type": "application/json",
            Accept: "text/event-stream",
          },
          body: JSON.stringify({ content }),
          signal,
        },
      );
      if (!res.ok || !res.body) throw new Error(`stream unavailable (${res.status})`);
    } catch (err) {
      if (signal?.aborted) throw err;
      const { data } = await chatAssistantService.sendMessage(sessionId, content);
      if (data?.assistantMessage) onToken(data.assistantMessage.content);
      return data;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let userMessage: ChatMessage | undefined;
    let assistantMessage: ChatMessage | undefined;
    let intent: string | undefined;
    let blocked = false;

    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // Events are separated by a blank line; the trailing piece may be half an event.
      const frames = buffer.split("\n\n");
      buffer = frames.pop() ?? "";

      for (const frame of frames) {
        const name = /^event:\s*(.*)$/m.exec(frame)?.[1]?.trim();
        const raw = /^data:\s*(.*)$/m.exec(frame)?.[1];
        if (!name || !raw) continue;
        let payload: Record<string, unknown>;
        try {
          payload = JSON.parse(raw);
        } catch {
          continue; // never let one malformed frame kill the stream
        }

        if (name === "token") {
          onToken(String(payload.t ?? ""));
        } else if (name === "start") {
          userMessage = payload.userMessage as ChatMessage;
          intent = payload.intent as string | undefined;
          blocked = Boolean(payload.blocked);
          if (userMessage) onStart?.(userMessage, intent, blocked);
        } else if (name === "done") {
          assistantMessage = payload.assistantMessage as ChatMessage;
        } else if (name === "error") {
          onToken(String(payload.message ?? ""));
        }
      }
    }

    return {
      userMessage: userMessage as ChatMessage,
      assistantMessage: assistantMessage as ChatMessage,
      intent,
      blocked,
    };
  },

  async renameSession(sessionId: string, title: string) {
    const { data } = await apiClient.put<ApiResponse<ChatSession>>(
      `${SESSIONS_ENDPOINT}/${sessionId}`,
      { title },
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

  async uploadDocument(
    file: File,
    title?: string,
    onProgress?: (p: UploadProgress) => void,
  ) {
    const form = new FormData();
    form.append("file", file);
    if (title) form.append("title", title);
    const { data } = await apiClient.post<ApiResponse<CompanyDocument>>(
      DOCUMENTS_ENDPOINT,
      form,
      {
        // Let the browser set the multipart boundary instead of the JSON default.
        headers: { "Content-Type": undefined },
        // Report byte-transfer progress so the UI can render a % bar + ETA.
        onUploadProgress: (evt) => {
          if (!onProgress) return;
          const total = evt.total ?? file.size;
          const loaded = evt.loaded ?? 0;
          const percent = total > 0 ? Math.round((loaded / total) * 100) : 0;
          onProgress({ loaded, total, percent });
        },
      },
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
