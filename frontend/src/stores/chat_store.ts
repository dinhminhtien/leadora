import { create } from "zustand";

// The active Lia conversation is kept per browser-tab session: it survives the
// panel being toggled open/closed and a page reload within the same tab, but a
// brand-new tab/session starts blank. We persist only the session id (the
// messages themselves live in the backend and are re-fetched by that id).
const SESSION_KEY = "lia-active-session";

function loadSession(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return sessionStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

function persistSession(id: string | null): void {
  if (typeof window === "undefined") return;
  try {
    if (id) sessionStorage.setItem(SESSION_KEY, id);
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    /* ignore storage failures */
  }
}

type ChatStore = {
  /** The ongoing Lia conversation for this tab session (null = blank/new chat). */
  selectedSessionId: string | null;
  setSelectedSessionId: (sessionId: string) => void;
  clearSelectedSession: () => void;

  /** Whether the floating Lia panel is open. */
  isOpen: boolean;
  openAssistant: () => void;
  closeAssistant: () => void;
  toggleAssistant: () => void;
};

export const useChatStore = create<ChatStore>((set) => ({
  selectedSessionId: loadSession(),
  setSelectedSessionId: (selectedSessionId) => {
    persistSession(selectedSessionId);
    set({ selectedSessionId });
  },
  clearSelectedSession: () => {
    persistSession(null);
    set({ selectedSessionId: null });
  },

  isOpen: false,
  openAssistant: () => set({ isOpen: true }),
  closeAssistant: () => set({ isOpen: false }),
  toggleAssistant: () => set((s) => ({ isOpen: !s.isOpen })),
}));
