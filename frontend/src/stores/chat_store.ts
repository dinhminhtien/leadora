import { create } from "zustand";

type ChatStore = {
  /** Currently open chat session in the assistant. */
  selectedSessionId: string | null;
  setSelectedSessionId: (sessionId: string) => void;
  clearSelectedSession: () => void;

  /** Whether the floating Lia assistant panel is open. */
  isOpen: boolean;
  openAssistant: () => void;
  closeAssistant: () => void;
  toggleAssistant: () => void;
};

export const useChatStore = create<ChatStore>((set) => ({
  selectedSessionId: null,
  setSelectedSessionId: (selectedSessionId) => set({ selectedSessionId }),
  clearSelectedSession: () => set({ selectedSessionId: null }),

  isOpen: false,
  openAssistant: () => set({ isOpen: true }),
  closeAssistant: () => set({ isOpen: false }),
  toggleAssistant: () => set((s) => ({ isOpen: !s.isOpen })),
}));
