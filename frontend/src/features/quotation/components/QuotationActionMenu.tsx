"use client";

import React from "react";
import { MoreVertical, type LucideIcon } from "lucide-react";

export type QuotationMenuAction = {
  key: string;
  label: string;
  Icon: LucideIcon;
  onClick: () => void;
  tone?: "primary" | "danger" | "default";
};

/** Row-level overflow menu for secondary quotation actions (Revise, Close, Remind, ...). */
export function QuotationActionMenu({
  actions,
  isOpen,
  onToggle,
  onClose,
}: {
  actions: QuotationMenuAction[];
  isOpen: boolean;
  onToggle: () => void;
  onClose: () => void;
}) {
  if (actions.length === 0) return null;

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={onToggle}
        title="More actions"
        aria-haspopup="true"
        aria-expanded={isOpen}
        className={`flex items-center justify-center size-7 rounded-lg border transition ${
          isOpen
            ? "border-blue-300 bg-blue-50 text-blue-600"
            : "border-slate-200 text-slate-400 hover:bg-slate-50 hover:text-slate-600"
        }`}
      >
        <MoreVertical className="size-3.5" />
      </button>

      {isOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={onClose} />
          <div className="absolute right-0 top-full mt-1 w-40 rounded-xl border border-slate-200 bg-white p-1 shadow-lg z-50 animate-in fade-in slide-in-from-top-1 duration-150">
            {actions.map((a) => (
              <button
                key={a.key}
                type="button"
                onClick={() => {
                  onClose();
                  a.onClick();
                }}
                className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-[11px] font-semibold transition ${
                  a.tone === "danger"
                    ? "text-red-600 hover:bg-red-50"
                    : a.tone === "primary"
                    ? "text-blue-600 hover:bg-blue-50"
                    : "text-slate-600 hover:bg-slate-50"
                }`}
              >
                <a.Icon className="size-3.5 shrink-0" />
                {a.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
