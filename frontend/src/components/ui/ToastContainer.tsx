"use client";

import { X, CheckCircle2, XCircle, AlertTriangle, Info } from "lucide-react";
import { useToastStore, type ToastVariant } from "@/stores/toast_store";

const VARIANT_STYLES: Record<ToastVariant, { container: string; icon: string }> = {
  success: {
    container: "bg-white border-emerald-200 text-emerald-800",
    icon: "text-emerald-500",
  },
  error: {
    container: "bg-white border-red-200 text-red-800",
    icon: "text-red-500",
  },
  warning: {
    container: "bg-white border-amber-200 text-amber-800",
    icon: "text-amber-500",
  },
  info: {
    container: "bg-white border-blue-200 text-blue-800",
    icon: "text-blue-500",
  },
};

const VARIANT_ICONS: Record<ToastVariant, React.ReactNode> = {
  success: <CheckCircle2 className="size-4 shrink-0" />,
  error: <XCircle className="size-4 shrink-0" />,
  warning: <AlertTriangle className="size-4 shrink-0" />,
  info: <Info className="size-4 shrink-0" />,
};

export function ToastContainer() {
  const { toasts, removeToast } = useToastStore();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed top-4 right-4 z-[9999] flex flex-col gap-2 w-80 max-w-[calc(100vw-2rem)]">
      {toasts.map((t) => {
        const styles = VARIANT_STYLES[t.variant];
        return (
          <div
            key={t.id}
            className={`flex items-start gap-3 rounded-xl border px-4 py-3 shadow-lg text-sm font-medium ${styles.container}`}
          >
            <span className={styles.icon}>{VARIANT_ICONS[t.variant]}</span>
            <span className="flex-1 leading-snug">{t.message}</span>
            <button
              onClick={() => removeToast(t.id)}
              className="shrink-0 opacity-50 hover:opacity-100 transition"
            >
              <X className="size-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
