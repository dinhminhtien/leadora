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
  /**
   * True when background ingestion failed and the document holds no searchable chunks. The row is
   * kept (rather than deleted) so the failure is visible instead of the upload silently vanishing.
   */
  failed?: boolean;
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

    const handleFrame = (frame: string) => {
      const name = /^event:\s*(.*)$/m.exec(frame)?.[1]?.trim();
      const raw = /^data:\s*(.*)$/m.exec(frame)?.[1];
      if (!name || !raw) return;
      let payload: Record<string, unknown>;
      try {
        payload = JSON.parse(raw);
      } catch {
        return; // never let one malformed frame kill the stream
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
    };

    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // Events are separated by a blank line; the trailing piece may be half an event.
        const frames = buffer.split("\n\n");
        buffer = frames.pop() ?? "";
        for (const frame of frames) handleFrame(frame);
      }
      // The loop above only ever parses what a blank line has closed off, so a terminal event
      // that arrives without its trailing blank line — a server completing the response right
      // after the write, a proxy trimming it — was left sitting in the buffer and dropped. That
      // event is usually `done`, i.e. exactly the one carrying the finished message.
      buffer += decoder.decode();
      if (buffer.trim()) handleFrame(buffer);
    } catch (err) {
      if (signal?.aborted) throw err;
      // The fallback below used to cover only the connection being refused, not the stream dying
      // midway, so a network blip after the first byte threw out of here with no reply at all.
      //
      // Retrying is only safe while the server has no record of the turn. `start` is emitted
      // straight after the question is persisted, and the gap between that and the first token
      // spans the whole context gather plus the model's prefill — several seconds. Retrying on
      // "no token yet" therefore resent questions the server had already stored, giving the
      // session the same question twice and paying for a second model call. `start` not having
      // arrived is the real signal that nothing was written.
      if (userMessage === undefined) {
        const { data } = await chatAssistantService.sendMessage(sessionId, content);
        if (data?.assistantMessage) onToken(data.assistantMessage.content);
        return data;
      }
      // The turn is already recorded: keep whatever arrived rather than replacing it with an
      // error, and let the caller reconcile against the server copy.
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
