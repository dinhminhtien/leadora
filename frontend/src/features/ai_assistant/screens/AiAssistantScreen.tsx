"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Bot,
  Sparkles,
  Send,
  User,
  Plus,
  Trash2,
  Pencil,
  FileText,
  Upload,
  Loader2,
  MessageSquare,
  ShieldAlert,
} from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { useChatStore } from "@/stores/chat_store";
import type { ChatMessage } from "@/services/chat_assistant_service";
import {
  useChatMessages,
  useChatSessions,
  useCompanyDocuments,
  useCreateChatSession,
  useDeleteChatSession,
  useDeleteDocument,
  useRenameChatSession,
  useSendChatMessage,
  useUploadDocument,
} from "@/features/ai_assistant/hooks/use_chat_sessions";

const SUGGESTIONS = [
  "Tôi có bao nhiêu deal đang mở?",
  "Liệt kê công việc của tôi đang quá hạn",
  "Tổng hợp doanh số toàn đội tháng này",
  "Theo nội quy công ty, chính sách hoàn huỷ booking thế nào?",
];

function formatTime(iso?: string) {
  if (!iso) return "";
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function AiAssistantScreen() {
  const { selectedSessionId, setSelectedSessionId, clearSelectedSession } =
    useChatStore();

  const sessionsQuery = useChatSessions();
  const messagesQuery = useChatMessages(selectedSessionId);
  const createSession = useCreateChatSession();
  const sendMessage = useSendChatMessage();
  const renameSession = useRenameChatSession();
  const deleteSession = useDeleteChatSession();

  const documentsQuery = useCompanyDocuments();
  const uploadDocument = useUploadDocument();
  const deleteDocument = useDeleteDocument();

  const [inputVal, setInputVal] = useState("");
  const [pendingUserText, setPendingUserText] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const feedRef = useRef<HTMLDivElement>(null);

  const sessions = sessionsQuery.data?.data ?? [];
  const messages: ChatMessage[] = useMemo(
    () => messagesQuery.data?.data ?? [],
    [messagesQuery.data],
  );
  const documents = documentsQuery.data?.data ?? [];

  // Auto-select the most recent session when none is chosen.
  useEffect(() => {
    if (!selectedSessionId && sessions.length > 0) {
      setSelectedSessionId(sessions[0].sessionId);
    }
  }, [selectedSessionId, sessions, setSelectedSessionId]);

  // Keep the feed scrolled to the latest message.
  useEffect(() => {
    feedRef.current?.scrollTo({ top: feedRef.current.scrollHeight });
  }, [messages, pendingUserText, sendMessage.isPending]);

  const ensureSession = async (): Promise<string | null> => {
    if (selectedSessionId) return selectedSessionId;
    const created = await createSession.mutateAsync(undefined);
    const id = created.data?.sessionId ?? null;
    if (id) setSelectedSessionId(id);
    return id;
  };

  const handleSend = async (textToSend?: string) => {
    const text = (textToSend ?? inputVal).trim();
    if (!text || sendMessage.isPending) return;

    const sessionId = await ensureSession();
    if (!sessionId) return;

    setInputVal("");
    setPendingUserText(text);
    try {
      await sendMessage.mutateAsync({ sessionId, content: text });
    } finally {
      setPendingUserText(null);
    }
  };

  const handleNewChat = async () => {
    clearSelectedSession();
    setPendingUserText(null);
    const created = await createSession.mutateAsync(undefined);
    if (created.data?.sessionId) setSelectedSessionId(created.data.sessionId);
  };

  const handleDeleteSession = async (sessionId: string) => {
    await deleteSession.mutateAsync(sessionId);
    if (sessionId === selectedSessionId) clearSelectedSession();
  };

  const handleRenameSession = async (sessionId: string, currentTitle?: string) => {
    const next = window.prompt("Đổi tên cuộc trò chuyện:", currentTitle ?? "");
    const title = next?.trim();
    if (!title || title === currentTitle) return;
    await renameSession.mutateAsync({ sessionId, title });
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await uploadDocument.mutateAsync({ file });
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="flex h-[calc(100vh-140px)] flex-col space-y-4">
      {/* Header */}
      <div className="flex shrink-0 items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold text-slate-800">
            <Sparkles className="size-5 animate-pulse text-blue-600" />
            Trợ lý nội bộ Leadora
          </h1>
          <p className="text-xs text-slate-400">
            Hỏi về dữ liệu bán hàng của bạn, tổng hợp toàn đội, hoặc tra cứu tài
            liệu công ty. Chỉ đọc — không thực hiện thao tác thay đổi dữ liệu.
          </p>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-4 lg:flex-row">
        {/* Session list */}
        <div className="flex w-full shrink-0 flex-col rounded-2xl border border-slate-100 bg-white shadow-sm lg:w-60">
          <div className="border-b border-slate-100 p-3">
            <Button
              onClick={handleNewChat}
              disabled={createSession.isPending}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 py-2 text-xs font-semibold text-white hover:bg-blue-700"
            >
              <Plus className="size-4" /> Cuộc trò chuyện mới
            </Button>
          </div>
          <div className="custom-scrollbar flex-1 space-y-1 overflow-y-auto p-2">
            {sessionsQuery.isLoading && (
              <p className="p-2 text-[11px] text-slate-400">Đang tải...</p>
            )}
            {!sessionsQuery.isLoading && sessions.length === 0 && (
              <p className="p-2 text-[11px] text-slate-400">
                Chưa có cuộc trò chuyện nào.
              </p>
            )}
            {sessions.map((s) => (
              <div
                key={s.sessionId}
                className={`group flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-2 text-[11px] transition ${
                  s.sessionId === selectedSessionId
                    ? "bg-blue-50 text-blue-700"
                    : "text-slate-600 hover:bg-slate-50"
                }`}
                onClick={() => setSelectedSessionId(s.sessionId)}
              >
                <span className="flex items-center gap-2 truncate">
                  <MessageSquare className="size-3.5 shrink-0" />
                  <span className="truncate">{s.title || "Cuộc trò chuyện"}</span>
                </span>
                <span className="ml-1 flex shrink-0 items-center gap-1 opacity-0 transition group-hover:opacity-100">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleRenameSession(s.sessionId, s.title);
                    }}
                    className="text-slate-300 transition hover:text-blue-500"
                    title="Đổi tên"
                  >
                    <Pencil className="size-3.5" />
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteSession(s.sessionId);
                    }}
                    className="text-slate-300 transition hover:text-red-500"
                    title="Xoá cuộc trò chuyện"
                  >
                    <Trash2 className="size-3.5" />
                  </button>
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Chat feed */}
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
          <div
            ref={feedRef}
            className="custom-scrollbar flex-1 space-y-4 overflow-y-auto p-4"
          >
            {messages.length === 0 && !pendingUserText && (
              <div className="flex h-full flex-col items-center justify-center text-center text-slate-400">
                <Bot className="mb-2 size-8 text-blue-300" />
                <p className="text-xs">
                  Xin chào! Tôi có thể tra cứu lead/deal/task của bạn, tổng hợp
                  số liệu toàn đội và trả lời theo tài liệu công ty.
                </p>
              </div>
            )}

            {messages.map((msg) => (
              <MessageBubble key={msg.messageId} message={msg} />
            ))}

            {/* Optimistic user bubble while awaiting the reply */}
            {pendingUserText && (
              <RawBubble role="USER" text={pendingUserText} />
            )}
            {sendMessage.isPending && (
              <div className="flex items-center gap-2 text-[11px] text-slate-400">
                <Loader2 className="size-3.5 animate-spin" /> Trợ lý đang soạn câu
                trả lời...
              </div>
            )}
          </div>

          {/* Input */}
          <div className="shrink-0 border-t border-slate-100 bg-slate-50/50 p-3">
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Hỏi: 'Deal đang mở của tôi?' hoặc 'Doanh số toàn đội tháng này'..."
                value={inputVal}
                onChange={(e) => setInputVal(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSend()}
                disabled={sendMessage.isPending}
                className="flex-1 rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs text-slate-800 placeholder-slate-400 transition focus:border-blue-500 focus:outline-none disabled:opacity-60"
              />
              <Button
                onClick={() => handleSend()}
                disabled={sendMessage.isPending}
                className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-blue-600 font-semibold text-white transition hover:bg-blue-700"
              >
                <Send className="size-4" />
              </Button>
            </div>
          </div>
        </div>

        {/* Right column: suggestions + documents */}
        <div className="flex w-full shrink-0 flex-col gap-4 lg:w-72">
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-bold uppercase tracking-wider text-slate-800">
                Gợi ý câu hỏi
              </CardTitle>
              <CardDescription className="text-[10px] text-slate-400">
                Bấm để hỏi nhanh
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              {SUGGESTIONS.map((prompt, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSend(prompt)}
                  disabled={sendMessage.isPending}
                  className="w-full rounded-lg border border-slate-100 bg-slate-50 p-2.5 text-left text-[11px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 disabled:opacity-60"
                >
                  {prompt}
                </button>
              ))}
            </CardContent>
          </Card>

          <Card className="flex min-h-0 flex-1 flex-col border-slate-100 bg-white shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-800">
                <FileText className="size-3.5" /> Tài liệu công ty
              </CardTitle>
              <CardDescription className="text-[10px] text-slate-400">
                Nguồn tri thức cho RAG (PDF, DOCX, TXT, MD)
              </CardDescription>
            </CardHeader>
            <CardContent className="flex min-h-0 flex-1 flex-col space-y-2">
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.docx,.doc,.txt,.md"
                onChange={handleUpload}
                className="hidden"
              />
              <Button
                onClick={() => fileInputRef.current?.click()}
                disabled={uploadDocument.isPending}
                className="flex w-full items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 bg-slate-50 py-2 text-[11px] font-semibold text-slate-600 hover:border-blue-300 hover:text-blue-700"
              >
                {uploadDocument.isPending ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <Upload className="size-3.5" />
                )}
                Tải tài liệu lên
              </Button>
              {uploadDocument.isError && (
                <p className="text-[10px] text-red-500">
                  Tải lên thất bại. Kiểm tra định dạng tệp và thử lại.
                </p>
              )}
              <div className="custom-scrollbar min-h-0 flex-1 space-y-1 overflow-y-auto">
                {documents.length === 0 && (
                  <p className="text-[10px] text-slate-400">
                    Chưa có tài liệu nào.
                  </p>
                )}
                {documents.map((doc) => (
                  <div
                    key={doc.documentId}
                    className="group flex items-center justify-between rounded-md border border-slate-100 px-2 py-1.5 text-[10px] text-slate-600"
                  >
                    <span className="truncate" title={doc.title}>
                      {doc.title}{" "}
                      <span className="text-slate-300">({doc.chunkCount})</span>
                    </span>
                    <button
                      onClick={() => deleteDocument.mutate(doc.documentId)}
                      className="ml-1 shrink-0 text-slate-300 opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                      title="Xoá tài liệu"
                    >
                      <Trash2 className="size-3" />
                    </button>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const blocked =
    message.intentMatched === "MUTATION_BLOCKED" ||
    message.intentMatched === "OFF_TOPIC";
  return (
    <RawBubble
      role={message.role}
      text={message.content}
      timestamp={formatTime(message.createdAt)}
      blocked={blocked}
    />
  );
}

function RawBubble({
  role,
  text,
  timestamp,
  blocked,
}: {
  role: "USER" | "ASSISTANT";
  text: string;
  timestamp?: string;
  blocked?: boolean;
}) {
  const isUser = role === "USER";
  return (
    <div
      className={`flex max-w-[85%] gap-3 ${
        isUser ? "ml-auto flex-row-reverse" : ""
      }`}
    >
      <div
        className={`flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
          isUser
            ? "bg-slate-200 text-slate-700"
            : blocked
              ? "bg-amber-500 text-white"
              : "bg-blue-600 text-white"
        }`}
      >
        {isUser ? (
          <User className="size-4" />
        ) : blocked ? (
          <ShieldAlert className="size-4" />
        ) : (
          <Bot className="size-4" />
        )}
      </div>
      <div className="space-y-1">
        <div
          className={`whitespace-pre-line rounded-2xl px-4 py-2.5 text-xs leading-relaxed ${
            isUser
              ? "bg-blue-600 font-medium text-white"
              : blocked
                ? "border border-amber-200 bg-amber-50 text-amber-800"
                : "border border-slate-100 bg-slate-50 text-slate-700"
          }`}
        >
          {text}
        </div>
        {timestamp && (
          <div
            className={`text-[9px] font-semibold text-slate-400 ${
              isUser ? "text-right" : ""
            }`}
          >
            {timestamp}
          </div>
        )}
      </div>
    </div>
  );
}
