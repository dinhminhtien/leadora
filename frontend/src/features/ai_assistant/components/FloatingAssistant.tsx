"use client";

/*
 * The setState calls inside effects here are deliberate external-system syncs the
 * react-hooks/set-state-in-effect rule flags as false positives: syncing the live
 * viewport size + restoring the saved launcher position on mount, and resetting the
 * typewriter when a new reply arrives. They run once / on real external changes,
 * not in a render→setState loop.
 */
/* eslint-disable react-hooks/set-state-in-effect */

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Send,
  Plus,
  Trash2,
  Pencil,
  FileText,
  Upload,
  Loader2,
  MessageSquare,
  History,
  Minus,
  ShieldAlert,
  Bot,
} from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";

import { useChatStore } from "@/stores/chat_store";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
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
import { LiaMascot } from "./LiaMascot";

const LAUNCHER_SIZE = 66;
const PANEL_W = 384;
const MARGIN = 20;
const POS_KEY = "lia-launcher-pos";

const SUGGESTIONS = [
  "Tôi có bao nhiêu deal đang mở?",
  "Liệt kê công việc của tôi đang quá hạn",
  "Tổng hợp doanh số toàn đội tháng này",
  "Chính sách hoàn huỷ booking thế nào?",
];

type Pos = { x: number; y: number };

function clamp(v: number, min: number, max: number) {
  return Math.max(min, Math.min(max, v));
}

export function FloatingAssistant() {
  const currentUser = useAuthStore((s) => s.user);
  const role = getUserRole(currentUser);

  const { isOpen, openAssistant, closeAssistant } = useChatStore();

  const [mounted, setMounted] = useState(false);
  const [pos, setPos] = useState<Pos | null>(null);
  const [vp, setVp] = useState({ w: 1280, h: 800 });
  const dragRef = useRef<{
    startX: number;
    startY: number;
    offX: number;
    offY: number;
    moved: boolean;
  } | null>(null);

  // Restore saved launcher position + track the viewport so we can keep Lia on screen.
  useEffect(() => {
    setMounted(true);
    setVp({ w: window.innerWidth, h: window.innerHeight });
    try {
      const raw = localStorage.getItem(POS_KEY);
      if (raw) setPos(JSON.parse(raw));
    } catch {
      /* ignore malformed storage */
    }
    const onResize = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  // Default home = bottom-right; once dragged we use the stored absolute position (clamped).
  const launcher = useMemo<Pos>(() => {
    if (!pos) {
      return { x: vp.w - LAUNCHER_SIZE - MARGIN, y: vp.h - LAUNCHER_SIZE - MARGIN };
    }
    return {
      x: clamp(pos.x, MARGIN, Math.max(MARGIN, vp.w - LAUNCHER_SIZE - MARGIN)),
      y: clamp(pos.y, MARGIN, Math.max(MARGIN, vp.h - LAUNCHER_SIZE - MARGIN)),
    };
  }, [pos, vp]);

  const onPointerDown = (e: React.PointerEvent) => {
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      offX: e.clientX - launcher.x,
      offY: e.clientY - launcher.y,
      moved: false,
    };
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const d = dragRef.current;
    if (!d) return;
    if (!d.moved && Math.hypot(e.clientX - d.startX, e.clientY - d.startY) > 4) {
      d.moved = true;
    }
    if (d.moved) {
      setPos({
        x: clamp(e.clientX - d.offX, MARGIN, vp.w - LAUNCHER_SIZE - MARGIN),
        y: clamp(e.clientY - d.offY, MARGIN, vp.h - LAUNCHER_SIZE - MARGIN),
      });
    }
  };

  const onPointerUp = () => {
    const d = dragRef.current;
    dragRef.current = null;
    if (!d) return;
    if (d.moved) {
      try {
        localStorage.setItem(POS_KEY, JSON.stringify(launcher));
      } catch {
        /* ignore */
      }
    } else {
      // A tap (not a drag) toggles the panel.
      if (isOpen) closeAssistant();
      else openAssistant();
    }
  };

  if (!mounted || role === "ADMIN") return null;

  // Panel placement: open above the launcher, aligned to its nearest horizontal edge,
  // clamped to the viewport so it's always fully visible.
  const panelH = Math.min(620, vp.h - 2 * MARGIN);
  const panelLeft = clamp(
    launcher.x + LAUNCHER_SIZE / 2 < vp.w / 2
      ? launcher.x // launcher on left half → panel opens to the right
      : launcher.x + LAUNCHER_SIZE - PANEL_W, // else align right edges
    MARGIN,
    Math.max(MARGIN, vp.w - PANEL_W - MARGIN),
  );
  const panelTop = clamp(
    launcher.y - panelH - 14 >= MARGIN
      ? launcher.y - panelH - 14 // above the launcher
      : clamp(launcher.y + LAUNCHER_SIZE + 14, MARGIN, vp.h - panelH - MARGIN),
    MARGIN,
    Math.max(MARGIN, vp.h - panelH - MARGIN),
  );

  return (
    <>
      {isOpen && (
        <div
          style={{ left: panelLeft, top: panelTop, width: PANEL_W, height: panelH }}
          className="fixed z-[60] flex flex-col overflow-hidden rounded-3xl border border-teal-100 bg-white shadow-2xl shadow-teal-900/15 animate-in fade-in zoom-in-95 duration-200"
        >
          <AssistantPanel
            role={role}
            userName={currentUser?.name}
            onClose={closeAssistant}
          />
        </div>
      )}

      {/* Draggable Lia launcher */}
      <button
        type="button"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        style={{ left: launcher.x, top: launcher.y, width: LAUNCHER_SIZE, height: LAUNCHER_SIZE }}
        className="fixed z-[61] flex touch-none select-none items-center justify-center rounded-full bg-gradient-to-br from-teal-300 to-teal-500 shadow-xl shadow-teal-900/25 ring-4 ring-white transition-transform duration-150 hover:scale-105 active:scale-95 cursor-grab active:cursor-grabbing"
        title={isOpen ? "Thu nhỏ Lia" : "Mở trợ lý Lia"}
        aria-label="Trợ lý Lia"
      >
        <LiaMascot variant="head" size={48} />
        {!isOpen && (
          <span className="absolute -right-0.5 -top-0.5 size-3.5 rounded-full border-2 border-white bg-pink-400" />
        )}
      </button>
    </>
  );
}

/* ─────────────────────────── Panel ─────────────────────────── */

function AssistantPanel({
  role,
  userName,
  onClose,
}: {
  role: string;
  userName?: string;
  onClose: () => void;
}) {
  const {
    selectedSessionId,
    setSelectedSessionId,
    clearSelectedSession,
  } = useChatStore();

  const sessionsQuery = useChatSessions();
  const messagesQuery = useChatMessages(selectedSessionId);
  const createSession = useCreateChatSession();
  const sendMessage = useSendChatMessage();
  const renameSession = useRenameChatSession();
  const deleteSession = useDeleteChatSession();

  const documentsQuery = useCompanyDocuments();
  const uploadDocument = useUploadDocument();
  const deleteDocument = useDeleteDocument();
  const canManageDocs = role === "MANAGER";

  const [inputVal, setInputVal] = useState("");
  const [pendingUserText, setPendingUserText] = useState<string | null>(null);
  const [animateId, setAnimateId] = useState<string | null>(null);
  const [view, setView] = useState<"chat" | "history" | "docs">("chat");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const feedRef = useRef<HTMLDivElement>(null);

  const sessions = useMemo(
    () => sessionsQuery.data?.data ?? [],
    [sessionsQuery.data],
  );
  const messages: ChatMessage[] = useMemo(
    () => messagesQuery.data?.data ?? [],
    [messagesQuery.data],
  );
  const documents = documentsQuery.data?.data ?? [];

  // Keep the selection valid (auto-pick newest, drop a deleted one).
  useEffect(() => {
    if (sessionsQuery.isLoading) return;
    const exists =
      !!selectedSessionId && sessions.some((s) => s.sessionId === selectedSessionId);
    if (exists) return;
    if (sessions.length > 0) setSelectedSessionId(sessions[0].sessionId);
    else if (selectedSessionId) clearSelectedSession();
  }, [
    selectedSessionId,
    sessions,
    sessionsQuery.isLoading,
    setSelectedSessionId,
    clearSelectedSession,
  ]);

  const scrollToBottom = useCallback(() => {
    feedRef.current?.scrollTo({ top: feedRef.current.scrollHeight, behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, pendingUserText, sendMessage.isPending, scrollToBottom]);

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
    setView("chat");
    try {
      const res = await sendMessage.mutateAsync({ sessionId, content: text });
      const newId = res.data?.assistantMessage?.messageId;
      if (newId) setAnimateId(newId);
    } finally {
      setPendingUserText(null);
    }
  };

  const handleNewChat = async () => {
    clearSelectedSession();
    setPendingUserText(null);
    setView("chat");
    const created = await createSession.mutateAsync(undefined);
    if (created.data?.sessionId) setSelectedSessionId(created.data.sessionId);
  };

  const handleDeleteSession = async (sessionId: string) => {
    if (sessionId === selectedSessionId) {
      const remaining = sessions.filter((s) => s.sessionId !== sessionId);
      if (remaining.length > 0) setSelectedSessionId(remaining[0].sessionId);
      else clearSelectedSession();
    }
    await deleteSession.mutateAsync(sessionId);
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
    <>
      {/* Header */}
      <div className="flex items-center gap-2.5 bg-gradient-to-r from-teal-400 to-teal-500 px-3.5 py-2.5 text-white">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-white/20">
          <LiaMascot variant="head" size={30} animated={false} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-bold leading-tight">Lia</p>
          <p className="truncate text-[10px] text-teal-50/90">
            Trợ lý chăm sóc &amp; follow-up lead của bạn
          </p>
        </div>
        <HeaderBtn title="Cuộc trò chuyện mới" onClick={handleNewChat} disabled={createSession.isPending}>
          <Plus className="size-4" />
        </HeaderBtn>
        <HeaderBtn
          title="Lịch sử trò chuyện"
          onClick={() => setView(view === "history" ? "chat" : "history")}
          active={view === "history"}
        >
          <History className="size-4" />
        </HeaderBtn>
        {canManageDocs && (
          <HeaderBtn
            title="Tài liệu công ty"
            onClick={() => setView(view === "docs" ? "chat" : "docs")}
            active={view === "docs"}
          >
            <FileText className="size-4" />
          </HeaderBtn>
        )}
        <HeaderBtn title="Thu nhỏ" onClick={onClose}>
          <Minus className="size-4" />
        </HeaderBtn>
      </div>

      {/* Body */}
      {view === "history" ? (
        <HistoryView
          sessions={sessions}
          loading={sessionsQuery.isLoading}
          selectedId={selectedSessionId}
          onSelect={(id) => {
            setSelectedSessionId(id);
            setView("chat");
          }}
          onRename={handleRenameSession}
          onDelete={handleDeleteSession}
        />
      ) : view === "docs" ? (
        <DocsView
          documents={documents}
          uploading={uploadDocument.isPending}
          uploadError={uploadDocument.isError}
          onUploadClick={() => fileInputRef.current?.click()}
          onDelete={(id, title, chunks) => {
            if (
              window.confirm(
                `Xoá tài liệu "${title}"?\nToàn bộ ${chunks} chunk của nó sẽ bị xoá khỏi bộ nhớ chat (không thể hoàn tác).`,
              )
            ) {
              deleteDocument.mutate(id);
            }
          }}
          deleting={deleteDocument.isPending}
          fileInputRef={fileInputRef}
          onFileChange={handleUpload}
        />
      ) : (
        <div ref={feedRef} className="flex-1 space-y-3 overflow-y-auto bg-slate-50/60 p-3 custom-scrollbar">
          {messages.length === 0 && !pendingUserText && (
            <EmptyState userName={userName} onPick={handleSend} />
          )}
          {messages.map((msg) => (
            <MessageBubble
              key={msg.messageId}
              message={msg}
              animate={msg.role === "ASSISTANT" && msg.messageId === animateId}
              onType={scrollToBottom}
            />
          ))}
          {pendingUserText && <RawBubble role="USER" text={pendingUserText} />}
          {sendMessage.isPending && (
            <div className="flex items-center gap-2 pl-1 text-[11px] text-teal-600">
              <Loader2 className="size-3.5 animate-spin" /> Lia đang soạn câu trả lời…
            </div>
          )}
        </div>
      )}

      {/* Input (chat view only) */}
      {view === "chat" && (
        <div className="border-t border-slate-100 bg-white p-2.5">
          <div className="flex items-end gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-2.5 py-1.5 focus-within:border-teal-400 focus-within:bg-white transition">
            <textarea
              rows={1}
              placeholder="Hỏi Lia về lead, deal, task, doanh số…"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              disabled={sendMessage.isPending}
              className="max-h-24 flex-1 resize-none bg-transparent py-1 text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none disabled:opacity-60"
            />
            <button
              onClick={() => handleSend()}
              disabled={sendMessage.isPending || !inputVal.trim()}
              className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-teal-500 text-white transition hover:bg-teal-600 disabled:opacity-40"
              title="Gửi"
            >
              <Send className="size-4" />
            </button>
          </div>
          <p className="mt-1 text-center text-[9px] text-slate-400">
            Lia chỉ đọc dữ liệu — không thực hiện thao tác thay đổi.
          </p>
        </div>
      )}
    </>
  );
}

/* ─────────────────────── Sub-views & bits ─────────────────────── */

function HeaderBtn({
  children,
  onClick,
  title,
  active,
  disabled,
}: {
  children: React.ReactNode;
  onClick: () => void;
  title: string;
  active?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`flex size-7 shrink-0 items-center justify-center rounded-lg transition disabled:opacity-50 ${
        active ? "bg-white/30" : "hover:bg-white/20"
      }`}
    >
      {children}
    </button>
  );
}

function EmptyState({
  userName,
  onPick,
}: {
  userName?: string;
  onPick: (text: string) => void;
}) {
  return (
    <div className="flex h-full flex-col items-center justify-center px-3 text-center">
      <LiaMascot variant="full" size={104} />
      <p className="mt-2 text-xs font-bold text-slate-700">
        Chào {userName?.split(" ").slice(-1)[0] || "bạn"}! Mình là Lia 💚
      </p>
      <p className="mt-1 text-[11px] leading-relaxed text-slate-500">
        Hỏi mình về lead/deal/task của bạn, số liệu toàn đội hoặc tài liệu công ty.
      </p>
      <div className="mt-3 grid w-full gap-1.5">
        {SUGGESTIONS.map((s) => (
          <button
            key={s}
            onClick={() => onPick(s)}
            className="rounded-xl border border-slate-100 bg-white px-2.5 py-2 text-left text-[11px] font-medium text-slate-600 shadow-sm transition hover:border-teal-200 hover:bg-teal-50 hover:text-teal-700"
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}

function HistoryView({
  sessions,
  loading,
  selectedId,
  onSelect,
  onRename,
  onDelete,
}: {
  sessions: { sessionId: string; title?: string }[];
  loading: boolean;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onRename: (id: string, title?: string) => void;
  onDelete: (id: string) => void;
}) {
  return (
    <div className="flex-1 space-y-1 overflow-y-auto bg-slate-50/60 p-2 custom-scrollbar">
      {loading && <p className="p-2 text-[11px] text-slate-400">Đang tải…</p>}
      {!loading && sessions.length === 0 && (
        <p className="p-2 text-[11px] text-slate-400">Chưa có cuộc trò chuyện nào.</p>
      )}
      {sessions.map((s) => (
        <div
          key={s.sessionId}
          onClick={() => onSelect(s.sessionId)}
          className={`group flex cursor-pointer items-center justify-between rounded-xl px-2.5 py-2 text-[11px] transition ${
            s.sessionId === selectedId
              ? "bg-teal-50 text-teal-700"
              : "bg-white text-slate-600 hover:bg-slate-100"
          }`}
        >
          <span className="flex min-w-0 items-center gap-2">
            <MessageSquare className="size-3.5 shrink-0" />
            <span className="truncate">{s.title || "Cuộc trò chuyện"}</span>
          </span>
          <span className="ml-1 flex shrink-0 items-center gap-1 opacity-0 transition group-hover:opacity-100">
            <button
              onClick={(e) => {
                e.stopPropagation();
                onRename(s.sessionId, s.title);
              }}
              className="text-slate-400 transition hover:text-teal-600"
              title="Đổi tên"
            >
              <Pencil className="size-3.5" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDelete(s.sessionId);
              }}
              className="text-slate-400 transition hover:text-rose-500"
              title="Xoá"
            >
              <Trash2 className="size-3.5" />
            </button>
          </span>
        </div>
      ))}
    </div>
  );
}

function DocsView({
  documents,
  uploading,
  uploadError,
  onUploadClick,
  onDelete,
  deleting,
  fileInputRef,
  onFileChange,
}: {
  documents: { documentId: string; title: string; chunkCount: number }[];
  uploading: boolean;
  uploadError: boolean;
  onUploadClick: () => void;
  onDelete: (id: string, title: string, chunks: number) => void;
  deleting: boolean;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <div className="flex-1 space-y-2 overflow-y-auto bg-slate-50/60 p-3 custom-scrollbar">
      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.docx,.doc,.txt,.md"
        onChange={onFileChange}
        className="hidden"
      />
      <button
        onClick={onUploadClick}
        disabled={uploading}
        className="flex w-full items-center justify-center gap-2 rounded-xl border border-dashed border-teal-300 bg-teal-50/50 py-2.5 text-[11px] font-semibold text-teal-700 transition hover:bg-teal-50"
      >
        {uploading ? <Loader2 className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
        Tải tài liệu lên (PDF, DOCX, TXT, MD)
      </button>
      {uploadError && (
        <p className="text-[10px] text-rose-500">Tải lên thất bại. Kiểm tra định dạng tệp và thử lại.</p>
      )}
      {documents.length === 0 && (
        <p className="pt-2 text-center text-[10px] text-slate-400">Chưa có tài liệu nào.</p>
      )}
      {documents.map((doc) => (
        <div
          key={doc.documentId}
          className="flex items-center justify-between rounded-xl border border-slate-100 bg-white px-2.5 py-2 text-[10px] text-slate-600"
        >
          <span className="truncate" title={doc.title}>
            {doc.title} <span className="text-slate-300">({doc.chunkCount} chunk)</span>
          </span>
          <button
            onClick={() => onDelete(doc.documentId, doc.title, doc.chunkCount)}
            disabled={deleting}
            className="ml-1 shrink-0 text-slate-400 transition hover:text-rose-500 disabled:opacity-40"
            title="Xoá tài liệu"
          >
            <Trash2 className="size-3.5" />
          </button>
        </div>
      ))}
    </div>
  );
}

/* ─────────────────────── Message rendering ─────────────────────── */

const MD_COMPONENTS: Components = {
  p: (props) => <p className="mb-1.5 last:mb-0" {...props} />,
  ul: (props) => <ul className="mb-1.5 list-disc space-y-0.5 pl-4 last:mb-0" {...props} />,
  ol: (props) => <ol className="mb-1.5 list-decimal space-y-0.5 pl-4 last:mb-0" {...props} />,
  li: (props) => <li className="leading-relaxed" {...props} />,
  strong: (props) => <strong className="font-semibold text-slate-900" {...props} />,
  a: (props) => <a className="text-teal-600 underline" target="_blank" rel="noreferrer" {...props} />,
  code: (props) => <code className="rounded bg-slate-200/70 px-1 py-0.5 font-mono text-[10px]" {...props} />,
  table: (props) => (
    <div className="my-1.5 overflow-x-auto">
      <table className="w-full border-collapse text-[10px]" {...props} />
    </div>
  ),
  thead: (props) => <thead className="bg-slate-100" {...props} />,
  th: (props) => <th className="border border-slate-200 px-1.5 py-1 text-left font-semibold" {...props} />,
  td: (props) => <td className="border border-slate-200 px-1.5 py-1 align-top" {...props} />,
};

function ChatMarkdown({ text }: { text: string }) {
  return (
    <div className="break-words">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={MD_COMPONENTS}>
        {text}
      </ReactMarkdown>
    </div>
  );
}

function Typewriter({ text, onType }: { text: string; onType?: () => void }) {
  const [shown, setShown] = useState(0);
  useEffect(() => {
    setShown(0);
    if (!text) return;
    const step = Math.max(2, Math.ceil(text.length / 90));
    let i = 0;
    const id = window.setInterval(() => {
      i = Math.min(text.length, i + step);
      setShown(i);
      onType?.();
      if (i >= text.length) window.clearInterval(id);
    }, 18);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);
  return <ChatMarkdown text={text.slice(0, shown)} />;
}

function MessageBubble({
  message,
  animate,
  onType,
}: {
  message: ChatMessage;
  animate?: boolean;
  onType?: () => void;
}) {
  const isUser = message.role === "USER";
  const blocked =
    message.intentMatched === "MUTATION_BLOCKED" || message.intentMatched === "OFF_TOPIC";
  if (isUser) return <RawBubble role="USER" text={message.content} />;
  return (
    <Shell role="ASSISTANT" blocked={blocked}>
      {animate ? <Typewriter text={message.content} onType={onType} /> : <ChatMarkdown text={message.content} />}
    </Shell>
  );
}

function RawBubble({ role, text }: { role: "USER" | "ASSISTANT"; text: string }) {
  return (
    <Shell role={role}>
      <div className="whitespace-pre-line">{text}</div>
    </Shell>
  );
}

function Shell({
  role,
  blocked,
  children,
}: {
  role: "USER" | "ASSISTANT";
  blocked?: boolean;
  children: React.ReactNode;
}) {
  const isUser = role === "USER";
  return (
    <div className={`flex max-w-[88%] gap-2 ${isUser ? "ml-auto flex-row-reverse" : ""}`}>
      <div
        className={`flex size-7 shrink-0 items-center justify-center rounded-full ${
          isUser ? "bg-slate-200 text-slate-600" : blocked ? "bg-amber-400 text-white" : "bg-teal-500 text-white"
        }`}
      >
        {isUser ? (
          <span className="text-[10px] font-bold">Bạn</span>
        ) : blocked ? (
          <ShieldAlert className="size-3.5" />
        ) : (
          <Bot className="size-3.5" />
        )}
      </div>
      <div
        className={`rounded-2xl px-3 py-2 text-xs leading-relaxed ${
          isUser
            ? "bg-teal-500 font-medium text-white"
            : blocked
              ? "border border-amber-200 bg-amber-50 text-amber-800"
              : "border border-slate-100 bg-white text-slate-700 shadow-sm"
        }`}
      >
        {children}
      </div>
    </div>
  );
}
