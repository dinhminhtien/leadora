import React from "react";
import Link from "next/link";
import { ShieldCheck } from "lucide-react";

export function LandingFooter() {
  return (
    <footer className="w-full relative overflow-hidden border-t border-border bg-zinc-50 dark:bg-zinc-950/60 py-10 px-6 mt-20">
      {/* Dynamic Watermark Background */}
      <div className="absolute inset-x-0 -bottom-8 z-0 text-center pointer-events-none select-none font-bold leading-none tracking-widest text-zinc-200/20 dark:text-zinc-900/10 text-[8rem] sm:text-[12rem] uppercase">
        Leadora
      </div>

      <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6 relative z-10">
        {/* Brand */}
        <div className="flex items-center gap-2.5">
          <img
            src="/logo1.jpg"
            alt="Leadora Logo"
            className="size-8 rounded-lg object-left object-cover mix-blend-multiply dark:mix-blend-normal dark:invert shrink-0"
          />
          <p className="text-xs text-muted-foreground">
            © {new Date().getFullYear()} Leadora. All rights reserved. Powered by NovaX.
          </p>
        </div>

        {/* Security badge and system info */}
        <div className="flex flex-wrap items-center gap-6 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5 font-medium">
            <ShieldCheck className="size-4 text-emerald-500" />
            AES-256 Encrypted Platform
          </span>
          <span>v1.0.0</span>
        </div>
      </div>
    </footer>
  );
}
