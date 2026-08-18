"use client";

import React from "react";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { ShieldCheck } from "lucide-react";

type PublicFeedbackLayoutProps = {
  children: React.ReactNode;
};

export function PublicFeedbackLayout({ children }: PublicFeedbackLayoutProps) {
  return (
    <div className="min-h-screen bg-background bg-dot-pattern flex flex-col font-sans">
      {/* Public Transactional Header */}
      <header className="border-b border-border bg-background/60 backdrop-blur-md sticky top-0 z-50">
        <div className="mx-auto max-w-360 w-full px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img
              src="/logo1.jpg"
              alt="Leadora Logo"
              className="size-8 rounded-xl object-left object-cover mix-blend-multiply dark:mix-blend-normal dark:invert shrink-0"
            />
            <div className="flex flex-col">
              <span className="text-xs font-bold text-foreground tracking-wider uppercase leading-none">Leadora</span>
              <span className="text-[8px] text-muted-foreground font-semibold tracking-widest uppercase mt-0.5 leading-none">Hotel CRM</span>
            </div>
          </div>
          <ThemeToggle />
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8">
        {children}
      </main>

      {/* Public Transactional Footer */}
      <footer className="border-t border-border/60 bg-background/40 py-6 text-center text-xs text-muted-foreground mt-auto">
        <div className="mx-auto max-w-360 w-full px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row justify-between items-center gap-4">
          <span className="font-semibold text-foreground/85">Leadora Hotel CRM</span>
          <span className="flex items-center gap-1.5 text-muted-foreground">
            <ShieldCheck className="size-4 text-emerald-500" />
            Secure Customer Verification • v1.0.0
          </span>
        </div>
      </footer>
    </div>
  );
}
