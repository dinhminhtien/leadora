import { create } from "zustand";

export type ToastVariant = "success" | "error" | "warning" | "info";

export type ToastItem = {
  id: string;
  variant: ToastVariant;
  message: string;
};

type ToastStore = {
  toasts: ToastItem[];
  addToast: (variant: ToastVariant, message: string) => void;
  removeToast: (id: string) => void;
};

let _counter = 0;

export const useToastStore = create<ToastStore>((set) => ({
  toasts: [],
  addToast: (variant, message) => {
    const id = `toast-${++_counter}`;
    set((s) => ({ toasts: [...s.toasts, { id, variant, message }] }));
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, 5000);
  },
  removeToast: (id) =>
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export const toast = {
  success: (message: string) => useToastStore.getState().addToast("success", message),
  error: (message: string) => useToastStore.getState().addToast("error", message),
  warning: (message: string) => useToastStore.getState().addToast("warning", message),
  info: (message: string) => useToastStore.getState().addToast("info", message),
};
