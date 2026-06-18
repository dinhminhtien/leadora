"use client";

import React, { useEffect, useState } from "react";
import { Sun, Moon } from "lucide-react";
import { useUiStore } from "@/stores/ui_store";

type ThemeToggleProps = {
  className?: string;
};

export function ThemeToggle({ className = "" }: ThemeToggleProps) {
  const { theme, toggleTheme } = useUiStore();
  const [mounted, setMounted] = useState(false);

  // Avoid hydration mismatch
  useEffect(() => {
    const handle = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(handle);
  }, []);

  if (!mounted) {
    return (
      <div 
        className={`w-9 h-9 rounded-xl border border-zinc-200/50 bg-white/70 dark:border-zinc-800/40 dark:bg-zinc-900/60 ${className}`} 
      />
    );
  }

  return (
    <button
      onClick={toggleTheme}
      className={`flex items-center justify-center size-9 rounded-xl border border-zinc-200/50 bg-white/70 shadow-sm transition-all duration-200 active:scale-95 hover:bg-zinc-50 dark:border-zinc-800/40 dark:bg-zinc-900/60 dark:hover:bg-zinc-800/50 cursor-pointer ${className}`}
      title={theme === "dark" ? "Switch to Light Mode" : "Switch to Dark Mode"}
      aria-label="Toggle theme"
    >
      {theme === "dark" ? (
        <Sun className="size-4 text-amber-400 animate-in fade-in zoom-in duration-300" />
      ) : (
        <Moon className="size-4 text-slate-700 dark:text-zinc-300 animate-in fade-in zoom-in duration-300" />
      )}
    </button>
  );
}
