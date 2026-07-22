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
  MessageSquare,
  History,
  FileText,
  Upload,
  Loader2,
  Minus,
  Maximize2,
  Minimize2,
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
  useStreamChatMessage,
  useUploadDocument,
} from "@/features/ai_assistant/hooks/use_chat_sessions";
import { LiaMascot } from "./LiaMascot";

const LAUNCHER_SIZE = 66;
const PANEL_W = 384;
const MARGIN = 20;
const POS_KEY = "lia-launcher-pos";

/** Max upload size — mirror of the backend cap (5 MB). Files above this are rejected client-side. */
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

function formatBytes(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

/** Human-friendly ETA from seconds remaining (empty when unknown/instant). */
function formatEta(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return "";
  if (seconds < 60) return `~${Math.ceil(seconds)}s`;
  return `~${Math.ceil(seconds / 60)} min`;
}

/** Transfer speed in human units (empty when not measurable yet). */
function formatSpeed(bytesPerSec: number) {
  if (!Number.isFinite(bytesPerSec) || bytesPerSec <= 0) return "";
  if (bytesPerSec >= 1024 * 1024) return `${(bytesPerSec / (1024 * 1024)).toFixed(1)} MB/s`;
  return `${Math.max(1, Math.round(bytesPerSec / 1024))} KB/s`;
}

/** Live state of an in-flight upload (byte transfer only — server ingest is async afterwards). */
type UploadState = {
  name: string;
  size: number;
  pct: number;
  loaded: number;
  speed: number; // bytes/s
  eta: number; // seconds remaining
};

const SUGGESTIONS = [
  "How many open deals do I have?",
  "List my overdue tasks",
  "Summarize the team's sales this month",
  "What is the booking cancellation policy?",
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
  const [fullscreen, setFullscreen] = useState(false);
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

  const closePanel = () => {
    setFullscreen(false);
    closeAssistant();
  };

  return (
    <>
      {isOpen && (
        <div
          style={fullscreen ? undefined : { left: panelLeft, top: panelTop, width: PANEL_W, height: panelH }}
          className={
            fullscreen
              ? "fixed inset-3 z-62 flex flex-col overflow-hidden rounded-2xl border border-primary/20 bg-white shadow-2xl shadow-black/10 animate-in fade-in zoom-in-95 duration-200"
              : "fixed z-60 flex flex-col overflow-hidden rounded-3xl border border-primary/20 bg-white shadow-2xl shadow-black/10 animate-in fade-in zoom-in-95 duration-200"
          }
        >
          <AssistantPanel
            role={role}
            userName={currentUser?.name}
            onClose={closePanel}
            isFullscreen={fullscreen}
            onToggleFullscreen={() => setFullscreen((f) => !f)}
          />
        </div>
      )}

      {/* Draggable Lia launcher (hidden while the panel is in fullscreen) */}
      {!fullscreen && (
      <button
        type="button"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        style={{ left: launcher.x, top: launcher.y, width: LAUNCHER_SIZE, height: LAUNCHER_SIZE }}
        className="fixed z-61 flex touch-none select-none items-center justify-center rounded-full bg-linear-to-br from-primary to-primary shadow-xl shadow-black/20 ring-4 ring-white transition-transform duration-150 hover:scale-105 active:scale-95 cursor-grab active:cursor-grabbing"
        title={isOpen ? "Minimize Lia" : "Open Lia assistant"}
        aria-label="Lia assistant"
      >
        <LiaMascot variant="head" size={48} />
        {!isOpen && (
          <span className="absolute -right-0.5 -top-0.5 size-3.5 rounded-full border-2 border-white bg-primary" />
        )}
      </button>
      )}
    </>
  );
}

/* ─────────────────────────── Panel ─────────────────────────── */

function AssistantPanel({
  role,
  userName,
  onClose,
  isFullscreen,
  onToggleFullscreen,
}: {
  role: string;
  userName?: string;
  onClose: () => void;
  isFullscreen: boolean;
  onToggleFullscreen: () => void;
}) {
  const { selectedSessionId, setSelectedSessionId, clearSelectedSession } = useChatStore();

  const messagesQuery = useChatMessages(selectedSessionId);
  const sessionsQuery = useChatSessions();
  const createSession = useCreateChatSession();
  const sendMessage = useStreamChatMessage();
  const renameSession = useRenameChatSession();
  const deleteSession = useDeleteChatSession();

  // Knowledge-base management (list/upload/delete) is Manager-only, both in the UI and
  // on the backend endpoint — non-managers must not even fire the documents query.
  const canManageDocs = role === "MANAGER";
  const documentsQuery = useCompanyDocuments(canManageDocs);
  const uploadDocument = useUploadDocument();
  const deleteDocument = useDeleteDocument();

  const [inputVal, setInputVal] = useState("");
  const [pendingUserText, setPendingUserText] = useState<string | null>(null);
  const [animateId, setAnimateId] = useState<string | null>(null);
  const [view, setView] = useState<"chat" | "history" | "docs">("chat");
  // Live upload state (name/size/%/speed/ETA) and the last upload error message.
  const [upload, setUpload] = useState<UploadState | null>(null);
  const [uploadErrorMsg, setUploadErrorMsg] = useState<string | null>(null);
  const uploadStartRef = useRef<number>(0);
  // Custom confirmation popup (replaces the native window.confirm for deletes).
  const [confirmDialog, setConfirmDialog] = useState<{
    title: string;
    message: string;
    confirmLabel: string;
    onConfirm: () => void;
  } | null>(null);
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

  const scrollToBottom = useCallback(() => {
    feedRef.current?.scrollTo({ top: feedRef.current.scrollHeight, behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, pendingUserText, sendMessage.isPending, sendMessage.streamingText,
      scrollToBottom]);

  // If the persisted session can't be loaded (deleted, different user after re-login,
  // or a reset DB), drop it and fall back to a fresh blank chat.
  useEffect(() => {
    if (selectedSessionId && messagesQuery.isError) clearSelectedSession();
  }, [selectedSessionId, messagesQuery.isError, clearSelectedSession]);

  // Create the conversation on the first message; the id is then kept for the whole
  // tab session (chat_store → sessionStorage), so toggling/reloading resumes it.
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
      // The reply has already been revealed token by token, so the type-on-arrival
      // animation would replay text the user has read — skip it for streamed turns.
      await sendMessage.mutateAsync({ sessionId, content: text });
    } finally {
      setPendingUserText(null);
    }
  };

  // Start a fresh blank conversation (abandons the current one for this tab).
  const handleNewChat = () => {
    clearSelectedSession();
    setPendingUserText(null);
    setInputVal("");
    setView("chat");
  };

  // Resume an older conversation from history.
  const handleSelectSession = (sessionId: string) => {
    setSelectedSessionId(sessionId);
    setPendingUserText(null);
    setView("chat");
  };

  const handleRenameSession = async (sessionId: string, currentTitle?: string) => {
    const next = window.prompt("Rename conversation:", currentTitle ?? "");
    const title = next?.trim();
    if (!title || title === currentTitle) return;
    await renameSession.mutateAsync({ sessionId, title });
  };

  const handleDeleteSession = (sessionId: string, title?: string) => {
    setConfirmDialog({
      title: "Delete conversation",
      message: `Delete "${title || "this conversation"}"? This can't be undone.`,
      confirmLabel: "Delete",
      onConfirm: () => {
        // If we're deleting the active conversation, fall back to a blank chat.
        if (sessionId === selectedSessionId) clearSelectedSession();
        deleteSession.mutate(sessionId);
      },
    });
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // Reset the input immediately so re-picking the SAME file still fires onChange.
    if (fileInputRef.current) fileInputRef.current.value = "";
    if (!file) return;

    setUploadErrorMsg(null);

    // Client-side size gate (backend enforces the same 5MB cap independently).
    if (file.size > MAX_UPLOAD_BYTES) {
      setUploadErrorMsg(
        `"${file.name}" is ${formatBytes(file.size)} — over the 5MB limit. Please pick a smaller file.`,
      );
      return;
    }

    uploadStartRef.current = Date.now();
    setUpload({ name: file.name, size: file.size, pct: 0, loaded: 0, speed: 0, eta: 0 });
    try {
      await uploadDocument.mutateAsync({
        file,
        onProgress: (p) => {
          const elapsed = (Date.now() - uploadStartRef.current) / 1000;
          const speed = elapsed > 0 ? p.loaded / elapsed : 0; // bytes/s
          const eta = speed > 0 ? (p.total - p.loaded) / speed : 0;
          setUpload((u) =>
            u ? { ...u, pct: p.percent, loaded: p.loaded, speed, eta } : u,
          );
        },
      });
    } catch (err) {
      // Surface the backend's real reason (e.g. unsupported format, >5MB) instead of a generic line.
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? "Upload failed. Please try again.";
      setUploadErrorMsg(msg);
    } finally {
      setUpload(null);
    }
  };

  const showEmpty = !selectedSessionId || (messages.length === 0 && !pendingUserText);

  return (
    <>
      {/* Header */}
      <div className="flex items-center gap-2.5 bg-linear-to-r from-primary to-primary px-3.5 py-2.5 text-white">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-white/20">
          <LiaMascot variant="head" size={30} animated={false} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-bold leading-tight">Lia</p>
          <p className="truncate text-[10px] text-white/90">
            Your lead care &amp; follow-up assistant
          </p>
        </div>
        <HeaderBtn title="New conversation" onClick={handleNewChat}>
          <Plus className="size-4" />
        </HeaderBtn>
        <HeaderBtn
          title="Chat history"
          onClick={() => setView(view === "history" ? "chat" : "history")}
          active={view === "history"}
        >
          <History className="size-4" />
        </HeaderBtn>
        {canManageDocs && (
          <HeaderBtn
            title="Company documents"
            onClick={() => setView(view === "docs" ? "chat" : "docs")}
            active={view === "docs"}
          >
            <FileText className="size-4" />
          </HeaderBtn>
        )}
        <HeaderBtn
          title={isFullscreen ? "Exit fullscreen" : "Fullscreen"}
          onClick={onToggleFullscreen}
        >
          {isFullscreen ? <Minimize2 className="size-4" /> : <Maximize2 className="size-4" />}
        </HeaderBtn>
        <HeaderBtn title="Minimize" onClick={onClose}>
          <Minus className="size-4" />
        </HeaderBtn>
      </div>

      {/* Body */}
      {view === "history" ? (
        <HistoryView
          sessions={sessions}
          loading={sessionsQuery.isLoading}
          selectedId={selectedSessionId}
          onSelect={handleSelectSession}
          onRename={handleRenameSession}
          onDelete={handleDeleteSession}
        />
      ) : view === "docs" ? (
        <DocsView
          documents={documents}
          uploading={uploadDocument.isPending}
          upload={upload}
          uploadErrorMsg={uploadErrorMsg}
          onUploadClick={() => fileInputRef.current?.click()}
          onDelete={(id, title, chunks) =>
            setConfirmDialog({
              title: "Delete document",
              message: `Delete "${title}"? All ${chunks} of its chunks will be removed from the assistant's memory. This can't be undone.`,
              confirmLabel: "Delete",
              onConfirm: () => deleteDocument.mutate(id),
            })
          }
          deleting={deleteDocument.isPending}
          fileInputRef={fileInputRef}
          onFileChange={handleUpload}
        />
      ) : (
        <div ref={feedRef} className="flex-1 overflow-y-auto bg-muted/60 custom-scrollbar">
          {showEmpty ? (
            <div className={`h-full p-3 ${isFullscreen ? "mx-auto w-full max-w-3xl" : ""}`}>
              <EmptyState userName={userName} onPick={handleSend} />
            </div>
          ) : (
            <div className={`space-y-3 p-3 ${isFullscreen ? "mx-auto w-full max-w-3xl" : ""}`}>
              {messages.map((msg) => (
                <MessageBubble
                  key={msg.messageId}
                  message={msg}
                  animate={msg.role === "ASSISTANT" && msg.messageId === animateId}
                  onType={scrollToBottom}
                />
              ))}
              {pendingUserText && <RawBubble role="USER" text={pendingUserText} />}
              {sendMessage.streamingText ? (
                <RawBubble role="ASSISTANT" text={sendMessage.streamingText} />
              ) : (
                sendMessage.isPending && (
                  <div className="flex items-center gap-2 pl-1 text-[11px] text-primary">
                    <Loader2 className="size-3.5 animate-spin" /> Lia is typing…
                  </div>
                )
              )}
            </div>
          )}
        </div>
      )}

      {/* Input (chat view only) */}
      {view === "chat" && (
        <div className="border-t border-border bg-white p-2.5">
         <div className={isFullscreen ? "mx-auto w-full max-w-3xl" : ""}>
          <div className="flex items-end gap-2 rounded-2xl border border-border bg-muted px-2.5 py-1.5 focus-within:border-primary focus-within:bg-white transition">
            <textarea
              rows={1}
              placeholder="Ask Lia about leads, deals, tasks, revenue…"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              disabled={sendMessage.isPending}
              className="max-h-24 flex-1 resize-none bg-transparent py-1 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none disabled:opacity-60"
            />
            <button
              onClick={() => handleSend()}
              disabled={sendMessage.isPending || !inputVal.trim()}
              className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-primary text-white transition hover:bg-primary/90 disabled:opacity-40"
              title="Send"
            >
              <Send className="size-4" />
            </button>
          </div>
          <p className="mt-1 text-center text-[9px] text-muted-foreground">
            Lia is read-only — it never changes any data.
          </p>
         </div>
        </div>
      )}

      {/* Custom confirmation popup (replaces window.confirm for deletes) */}
      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          confirmLabel={confirmDialog.confirmLabel}
          onCancel={() => setConfirmDialog(null)}
          onConfirm={() => {
            confirmDialog.onConfirm();
            setConfirmDialog(null);
          }}
        />
      )}
    </>
  );
}

/* ─────────────────────── Sub-views & bits ─────────────────────── */

function ConfirmDialog({
  title,
  message,
  confirmLabel,
  onCancel,
  onConfirm,
}: {
  title: string;
  message: string;
  confirmLabel: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  // Full-widget overlay + centered card. z-index sits above the panel (z-60/62) and launcher (z-61).
  return (
    <div
      className="absolute inset-0 z-70 flex items-center justify-center bg-black/40 p-4 animate-in fade-in duration-150"
      onClick={onCancel}
      role="dialog"
      aria-modal="true"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[280px] rounded-2xl bg-white p-4 shadow-2xl animate-in zoom-in-95 duration-150"
      >
        <div className="mb-2 flex items-start gap-2.5">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-danger/10 text-danger">
            <Trash2 className="size-4" />
          </div>
          <div className="min-w-0">
            <p className="text-[13px] font-bold text-foreground">{title}</p>
            <p className="mt-0.5 text-[11px] leading-relaxed text-muted-foreground">{message}</p>
          </div>
        </div>
        <div className="mt-3 flex justify-end gap-2">
          <button
            onClick={onCancel}
            className="rounded-lg px-3 py-1.5 text-[11px] font-semibold text-muted-foreground transition hover:bg-muted"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            autoFocus
            className="rounded-lg bg-danger px-3 py-1.5 text-[11px] font-semibold text-white transition hover:bg-danger/90"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

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
      <p className="mt-2 text-xs font-bold text-foreground">
        Hi {userName?.split(" ").slice(-1)[0] || "there"}! I'm Lia 💚
      </p>
      <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground">
        Ask me about your leads/deals/tasks, team-wide figures, or company documents.
      </p>
      <div className="mt-3 grid w-full gap-1.5">
        {SUGGESTIONS.map((s) => (
          <button
            key={s}
            onClick={() => onPick(s)}
            className="rounded-xl border border-border bg-white px-2.5 py-2 text-left text-[11px] font-medium text-muted-foreground shadow-sm transition hover:border-primary/30 hover:bg-primary/10 hover:text-primary"
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
  onDelete: (id: string, title?: string) => void;
}) {
  return (
    <div className="flex-1 space-y-1 overflow-y-auto bg-muted/60 p-2 custom-scrollbar">
      {loading && <p className="p-2 text-[11px] text-muted-foreground">Loading…</p>}
      {!loading && sessions.length === 0 && (
        <p className="p-2 text-[11px] text-muted-foreground">No conversations yet.</p>
      )}
      {sessions.map((s) => (
        <div
          key={s.sessionId}
          onClick={() => onSelect(s.sessionId)}
          className={`group flex cursor-pointer items-center justify-between rounded-xl px-2.5 py-2 text-[11px] transition ${
            s.sessionId === selectedId
              ? "bg-primary/10 text-primary"
              : "bg-white text-muted-foreground hover:bg-muted"
          }`}
        >
          <span className="flex min-w-0 items-center gap-2">
            <MessageSquare className="size-3.5 shrink-0" />
            <span className="truncate">{s.title || "Conversation"}</span>
          </span>
          <span className="ml-1 flex shrink-0 items-center gap-1 opacity-0 transition group-hover:opacity-100">
            <button
              onClick={(e) => {
                e.stopPropagation();
                onRename(s.sessionId, s.title);
              }}
              className="text-muted-foreground transition hover:text-primary"
              title="Rename"
            >
              <Pencil className="size-3.5" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDelete(s.sessionId, s.title);
              }}
              className="text-muted-foreground transition hover:text-danger"
              title="Delete"
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
  upload,
  uploadErrorMsg,
  onUploadClick,
  onDelete,
  deleting,
  fileInputRef,
  onFileChange,
}: {
  documents: {
    documentId: string;
    title: string;
    chunkCount: number;
    processing?: boolean;
  }[];
  uploading: boolean;
  upload: UploadState | null;
  uploadErrorMsg: string | null;
  onUploadClick: () => void;
  onDelete: (id: string, title: string, chunks: number) => void;
  deleting: boolean;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  // The byte transfer finishes at 100%; the backend then embeds the file in the background.
  const transferring = upload !== null && upload.pct < 100;
  const eta = upload ? formatEta(upload.eta) : "";
  const speed = upload ? formatSpeed(upload.speed) : "";
  return (
    <div className="flex-1 space-y-2 overflow-y-auto bg-muted/60 p-3 custom-scrollbar">
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
        className="flex w-full items-center justify-center gap-2 rounded-xl border border-dashed border-primary/40 bg-primary/10 py-2.5 text-[11px] font-semibold text-primary transition hover:bg-primary/10 disabled:opacity-60"
      >
        {uploading ? <Loader2 className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
        Upload a document (PDF, DOCX, TXT, MD)
      </button>
      <p className="text-center text-[9px] text-muted-foreground">Max 5MB per file.</p>

      {/* Rich upload progress: file name + size, % bar that fills at the real transfer speed,
          transferred/total, live speed and ETA. At 100% the byte upload is done and the server
          is embedding the file (async), so we switch the label to "Processing…". */}
      {upload && (
        <div className="rounded-xl border border-primary/20 bg-white px-2.5 py-2">
          <div className="mb-1 flex items-center gap-2 text-[10px] font-medium text-muted-foreground">
            <FileText className="size-3.5 shrink-0 text-primary" />
            <span className="min-w-0 flex-1 truncate" title={upload.name}>
              {upload.name}
            </span>
            <span className="shrink-0 text-muted-foreground">{formatBytes(upload.size)}</span>
          </div>
          <div className="mb-1 flex items-center justify-between text-[10px] font-medium text-muted-foreground">
            <span>{transferring ? "Uploading…" : "Processing on server…"}</span>
            <span className="tabular-nums text-primary">{upload.pct}%</span>
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary transition-[width] duration-150"
              style={{ width: `${upload.pct}%` }}
            />
          </div>
          {transferring ? (
            <p className="mt-1 flex justify-between text-[9px] text-muted-foreground">
              <span>
                {formatBytes(upload.loaded)} / {formatBytes(upload.size)}
                {speed && ` · ${speed}`}
              </span>
              {eta && <span>{eta} left</span>}
            </p>
          ) : (
            <p className="mt-1 text-[9px] text-muted-foreground">
              Uploaded — parsing &amp; indexing on the server…
            </p>
          )}
        </div>
      )}

      {uploadErrorMsg && (
        <p className="rounded-lg bg-danger/10 px-2 py-1.5 text-[10px] text-danger">{uploadErrorMsg}</p>
      )}

      {documents.length === 0 && (
        <p className="pt-2 text-center text-[10px] text-muted-foreground">No documents yet.</p>
      )}
      {documents.map((doc) => {
        const processing = doc.processing || doc.chunkCount === 0;
        return (
          <div
            key={doc.documentId}
            className="flex items-center justify-between rounded-xl border border-border bg-white px-2.5 py-2 text-[10px] text-muted-foreground"
          >
            <span className="flex min-w-0 items-center gap-1.5" title={doc.title}>
              <span className="truncate">{doc.title}</span>
              {processing ? (
                <span className="flex shrink-0 items-center gap-1 text-amber-500">
                  <Loader2 className="size-3 animate-spin" /> Processing…
                </span>
              ) : (
                <span className="shrink-0 text-muted-foreground">({doc.chunkCount} chunks)</span>
              )}
            </span>
            <button
              onClick={() => onDelete(doc.documentId, doc.title, doc.chunkCount)}
              disabled={deleting || processing}
              className="ml-1 shrink-0 text-muted-foreground transition hover:text-danger disabled:opacity-40"
              title={processing ? "Processing — cannot delete yet" : "Delete document"}
            >
              <Trash2 className="size-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}

/* ─────────────────────── Message rendering ─────────────────────── */

const MD_COMPONENTS: Components = {
  p: (props) => <p className="mb-1.5 last:mb-0" {...props} />,
  ul: (props) => <ul className="mb-1.5 list-disc space-y-0.5 pl-4 last:mb-0" {...props} />,
  ol: (props) => <ol className="mb-1.5 list-decimal space-y-0.5 pl-4 last:mb-0" {...props} />,
  li: (props) => <li className="leading-relaxed" {...props} />,
  strong: (props) => <strong className="font-semibold text-foreground" {...props} />,
  a: (props) => <a className="text-primary underline" target="_blank" rel="noreferrer" {...props} />,
  code: (props) => <code className="rounded bg-border/70 px-1 py-0.5 font-mono text-[10px]" {...props} />,
  table: (props) => (
    <div className="my-1.5 overflow-x-auto">
      <table className="w-full border-collapse text-[10px]" {...props} />
    </div>
  ),
  thead: (props) => <thead className="bg-muted" {...props} />,
  th: (props) => <th className="border border-border px-1.5 py-1 text-left font-semibold" {...props} />,
  td: (props) => <td className="border border-border px-1.5 py-1 align-top" {...props} />,
};

function ChatMarkdown({ text }: { text: string }) {
  return (
    <div className="wrap-break-word">
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
  // Normalise casing defensively so role alignment never flips on a backend quirk.
  const isUser = (message.role ?? "").toUpperCase() === "USER";
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
          isUser ? "bg-border text-muted-foreground" : blocked ? "bg-amber-400 text-white" : "bg-primary text-white"
        }`}
      >
        {isUser ? (
          <span className="text-[10px] font-bold">You</span>
        ) : blocked ? (
          <ShieldAlert className="size-3.5" />
        ) : (
          <Bot className="size-3.5" />
        )}
      </div>
      <div
        className={`rounded-2xl px-3 py-2 text-xs leading-relaxed ${
          isUser
            ? "bg-primary font-medium text-white"
            : blocked
              ? "border border-amber-200 bg-amber-50 text-amber-800"
              : "border border-border bg-white text-foreground shadow-sm"
        }`}
      >
        {children}
      </div>
    </div>
  );
}
