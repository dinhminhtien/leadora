import { create } from "zustand";

import type { User } from "@/shared/types/auth";

type AuthStore = {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setUser: (user: User) => void;
  clearUser: () => void;
  setLoading: (loading: boolean) => void;
  updateUserFields: (fields: Partial<Pick<User, "name" | "avatarUrl">>) => void;
};

export const useAuthStore = create<AuthStore>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  setUser: (user) => set({ user, isAuthenticated: true, isLoading: false }),
  clearUser: () =>
    set({ user: null, isAuthenticated: false, isLoading: false }),
  setLoading: (isLoading) => set({ isLoading }),
  updateUserFields: (fields) =>
    set((state) => ({
      user: state.user ? { ...state.user, ...fields } : null,
    })),
}));
