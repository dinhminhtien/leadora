"use client";

import React from "react";
import Link from "next/link";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { Button } from "@/components/ui/Button";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

export function LandingNavbar() {
  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: "smooth" });
    }
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/40 bg-background/95 backdrop-blur-md supports-backdrop-filter:bg-background/60">
      <div className="max-w-7xl mx-auto flex h-16 items-center justify-between px-6">
        {/* Brand/Logo */}
        <Link href="/" className="flex items-center gap-2.5 group">
          <img
            src="/logo1.jpg"
            alt="Leadora Logo"
            className="size-10 rounded-xl object-left object-cover mix-blend-multiply dark:mix-blend-normal dark:invert shrink-0"
          />
          <div className="flex flex-col">
            <span className="text-sm font-bold text-zinc-800 dark:text-zinc-100 tracking-wider">Leadora</span>
            <span className="text-[9px] text-zinc-400 dark:text-zinc-500 font-semibold tracking-widest uppercase">Hotel CRM</span>
          </div>
        </Link>

        {/* Desktop Navigation Links */}
        <nav className="hidden md:flex items-center gap-6 text-xs font-semibold text-muted-foreground">
          <button
            onClick={() => scrollToSection("features")}
            className="hover:text-foreground transition cursor-pointer"
          >
            Features
          </button>
          <button
            onClick={() => scrollToSection("stats")}
            className="hover:text-foreground transition cursor-pointer"
          >
            Metrics
          </button>
          <button
            onClick={() => scrollToSection("benefits")}
            className="hover:text-foreground transition cursor-pointer"
          >
            Benefits
          </button>
        </nav>

        {/* Actions */}
        <div className="flex items-center gap-3">
          <ThemeToggle />
          <Link href={ROUTE_PATHS.login}>
            <Button variant="ghost" size="sm" className="hidden sm:inline-flex rounded-lg text-xs font-semibold">
              Sign In
            </Button>
          </Link>
          <Link href={ROUTE_PATHS.login}>
            <Button variant="primary" size="sm" className="rounded-lg text-xs font-semibold">
              Get Started
            </Button>
          </Link>
        </div>
      </div>
    </header>
  );
}
