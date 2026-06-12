import { create } from "zustand";

type ChatStore = {
  selectedSessionId: string | null;
  setSelectedSessionId: (sessionId: string) => void;
  clearSelectedSession: () => void;
};

export const useChatStore = create<ChatStore>((set) => ({
  selectedSessionId: null,
  setSelectedSessionId: (selectedSessionId) => set({ selectedSessionId }),
  clearSelectedSession: () => set({ selectedSessionId: null }),
}));
