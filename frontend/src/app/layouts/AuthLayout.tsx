import React from "react";
import { Sparkles, Building2, TrendingUp, ShieldCheck, CheckCircle2, Star } from "lucide-react";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

type AuthLayoutProps = {
  children: React.ReactNode;
};

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <main className="min-h-screen grid grid-cols-1 lg:grid-cols-2 bg-background font-sans">
      {/* Left panel - Brand & Stats Showcase (visible on large viewports, adaptive to light/dark) */}
      <div className="hidden lg:flex flex-col justify-between bg-zinc-50 dark:bg-zinc-950 p-16 text-zinc-700 dark:text-zinc-100 relative overflow-hidden border-r border-zinc-200 dark:border-zinc-900">
        {/* Glow meshes */}
        <div className="absolute top-[-20%] left-[-20%] w-[60%] h-[60%] rounded-full bg-primary/5 dark:bg-primary/10 blur-[120px] pointer-events-none" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] rounded-full bg-accent/5 dark:bg-accent/10 blur-[120px] pointer-events-none" />
        <div className="absolute top-[30%] right-[10%] w-[35%] h-[35%] rounded-full bg-primary/5 blur-[100px] pointer-events-none" />

        {/* Brand Header */}
        <div className="flex items-center gap-3 relative z-10">
          <img
            src="/logo1.jpg"
            alt="Leadora Logo"
            className="size-9 rounded-xl object-left object-cover mix-blend-multiply dark:mix-blend-normal dark:invert shrink-0"
          />
          <div className="flex flex-col">
            <span className="text-xs font-bold text-zinc-800 dark:text-zinc-100 tracking-wider">Leadora</span>
            <span className="text-[9px] text-zinc-400 dark:text-zinc-500 font-semibold tracking-widest uppercase">Hotel CRM</span>
          </div>
        </div>

        {/* Center Content: Marketing Copy & Mini Dashboard Metrics */}
        <div className="space-y-10 relative z-10 my-auto">
          <div className="space-y-4 max-w-md">
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-primary/10 dark:bg-primary/20 border border-primary/20 dark:border-primary/30 text-[10px] font-bold text-primary dark:text-primary-foreground tracking-wider uppercase">
              <Sparkles className="size-3" />
              <span>Direct Sales Optimization</span>
            </div>
            <h2 className="text-3xl font-extrabold tracking-tight leading-tight text-zinc-900 dark:text-white">
              Elevate your hotel&apos;s sales performance.
            </h2>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
              A unified workspace to streamline guest handovers, automate booking pipelines, monitor SLA records, and boost revenue.
            </p>
          </div>

          {/* Mini Dashboard mock cards */}
          <div className="grid grid-cols-2 gap-4 max-w-lg">
            {/* Card 1 */}
            <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white/60 dark:bg-zinc-900/40 backdrop-blur-md hover:bg-white/80 dark:hover:bg-zinc-900/60 transition duration-200 shadow-[0_2px_8px_rgba(0,0,0,0.02)]">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wider">Pipeline Value</span>
                <TrendingUp className="size-3.5 text-emerald-500" />
              </div>
              <p className="text-xl font-bold text-zinc-900 dark:text-white mt-1.5">148.250 ₫</p>
              <span className="text-[9px] text-emerald-500 font-semibold">+12.4% this week</span>
            </div>

            {/* Card 2 */}
            <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white/60 dark:bg-zinc-900/40 backdrop-blur-md hover:bg-white/80 dark:hover:bg-zinc-900/60 transition duration-200 shadow-[0_2px_8px_rgba(0,0,0,0.02)]">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wider">SLA Response</span>
                <CheckCircle2 className="size-3.5 text-emerald-500" />
              </div>
              <p className="text-xl font-bold text-zinc-900 dark:text-white mt-1.5">99.8%</p>
              <span className="text-[9px] text-emerald-500 font-semibold">Outstanding rating</span>
            </div>

            {/* Card 3 */}
            <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white/60 dark:bg-zinc-900/40 backdrop-blur-md hover:bg-white/80 dark:hover:bg-zinc-900/60 transition duration-200 col-span-2 flex items-center justify-between gap-4 shadow-[0_2px_8px_rgba(0,0,0,0.02)]">
              <div className="flex items-center gap-3">
                <div className="size-8 rounded-lg bg-primary/10 dark:bg-primary/20 flex items-center justify-center">
                  <Star className="size-4 text-primary fill-primary/20" />
                </div>
                <div>
                  <p className="text-xs font-semibold text-zinc-800 dark:text-zinc-300">Direct Conversion Boost</p>
                  <p className="text-[10px] text-zinc-400 dark:text-zinc-500">Average increase in direct bookings</p>
                </div>
              </div>
              <p className="text-lg font-bold text-emerald-500">+24.5%</p>
            </div>
          </div>
        </div>

        {/* Footer info */}
        <div className="flex items-center justify-between text-[10px] text-zinc-400 dark:text-zinc-600 relative z-10 border-t border-zinc-200 dark:border-zinc-900 pt-6">
          <span className="flex items-center gap-1.5">
            <ShieldCheck className="size-4 text-emerald-500" />
            AES-256 Encrypted Platform
          </span>
          <span>v1.0.0</span>
        </div>
      </div>

      {/* Right panel - Auth Forms */}
      <div className="flex flex-col justify-center items-center px-6 py-12 lg:px-16 bg-background relative">
        {/* Glow for mobile viewport */}
        <div className="lg:hidden absolute top-0 left-0 w-full h-[30%] bg-linear-to-b from-primary/5 to-transparent pointer-events-none" />

        {/* Theme Toggle Button */}
        <div className="absolute top-6 right-6 z-20">
          <ThemeToggle />
        </div>

        <div className="w-full max-w-md space-y-6 relative z-10">

          {/* Header (visible on mobile, simplified brand on desktop layout) */}
          <div className="text-center lg:text-left space-y-1.5">
            <div className="lg:hidden flex items-center justify-center gap-2 mb-6">
              <img
                src="/logo1.jpg"
                alt="Leadora Logo"
                className="size-8 rounded-lg object-left object-cover mix-blend-multiply dark:mix-blend-normal dark:invert shrink-0"
              />
              <span className="text-sm font-bold tracking-wider text-foreground">Leadora</span>
            </div>
            <span className="text-[10px] font-bold uppercase tracking-widest text-primary">
              Welcome to Leadora
            </span>
            <h1 className="text-2xl font-extrabold tracking-tight text-foreground">
              Hotel sales workflow
            </h1>
          </div>

          <div className="transition-all duration-300">
            {children}
          </div>
        </div>
      </div>
    </main>
  );
}
