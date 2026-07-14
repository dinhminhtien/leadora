"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/stores/auth_store";
import { LandingNavbar } from "../components/LandingNavbar";
import { LandingFooter } from "../components/LandingFooter";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { 
  Sparkles, 
  Handshake, 
  ChartNoAxesCombined, 
  CalendarCheck, 
  Gauge, 
  FileText, 
  Star, 
  TrendingUp, 
  CheckCircle2, 
  ArrowRight,
  Smartphone,
  Clock,
  LayoutDashboard,
  Play,
  ArrowUpRight,
  ShieldCheck,
  ChevronRight,
  Users,
  Building2
} from "lucide-react";

export function LandingPage() {
  const { isAuthenticated, isLoading } = useAuthStore();
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<"sales" | "ops" | "ai">("sales");

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/dashboard");
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4 animate-pulse">
          <div className="size-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-sm font-medium text-muted-foreground">Loading Leadora...</p>
        </div>
      </div>
    );
  }

  if (isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4 animate-pulse">
          <div className="size-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-sm font-medium text-muted-foreground">Redirecting to Dashboard...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground bg-dot-pattern selection:bg-primary/20 selection:text-primary overflow-x-hidden">
      <LandingNavbar />

      <main className="flex-1 relative">
        {/* Glow meshes / Aurora effects */}
        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-primary/10 dark:bg-primary/20 blur-[150px] pointer-events-none" />
        <div className="absolute top-[30%] right-[-10%] w-[45%] h-[45%] rounded-full bg-[#1D9E75]/10 dark:bg-[#1D9E75]/15 blur-[150px] pointer-events-none" />
        <div className="absolute bottom-[20%] left-[-10%] w-[40%] h-[40%] rounded-full bg-indigo-500/10 dark:bg-indigo-500/15 blur-[150px] pointer-events-none" />

        {/* Hero Section */}
        <section className="relative pt-24 pb-20 px-6 max-w-7xl mx-auto text-center z-10">
          {/* Animated Product Announcement Pill */}
          <div className="flex justify-center mb-8">
            <Link href={ROUTE_PATHS.login} className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-zinc-100 hover:bg-zinc-200/80 dark:bg-zinc-900 dark:hover:bg-zinc-800/80 border border-zinc-250 dark:border-zinc-800 text-xs font-semibold text-zinc-800 dark:text-zinc-200 transition-all duration-200 hover:-translate-y-px active:translate-y-0 shadow-xs">
              <span className="flex size-1.5 rounded-full bg-emerald-500 animate-pulse" />
              <span className="font-medium text-zinc-500 dark:text-zinc-400">Leadora v1.0 is live</span>
              <span className="h-3 w-px bg-zinc-300 dark:bg-zinc-700" />
              <span className="flex items-center gap-0.5 text-primary dark:text-primary-foreground font-bold">
                Get Started Free <ChevronRight className="size-3" />
              </span>
            </Link>
          </div>

          {/* High-Impact Gradient Heading */}
          <h1 className="text-4xl sm:text-6xl md:text-7xl font-extrabold tracking-tight leading-[1.1] max-w-5xl mx-auto text-zinc-900 dark:text-white">
            Elevate your hotel&apos;s <br className="hidden md:block" />
            <span className="bg-clip-text text-transparent bg-linear-to-r from-primary via-indigo-500 to-[#1D9E75] dark:from-[#378ADD] dark:via-blue-400 dark:to-emerald-400">
              sales performance.
            </span>
          </h1>

          <p className="mt-6 text-sm sm:text-base md:text-lg text-muted-foreground max-w-2xl mx-auto leading-relaxed font-medium">
            A unified CRM & operations workspace replacing scattered spreadsheets. Streamline guest pipelines, monitor real-time SLA metrics, and boost direct booking revenue.
          </p>

          {/* Hero CTAs */}
          <div className="mt-10 flex flex-wrap justify-center gap-4">
            <Link href={ROUTE_PATHS.login}>
              <Button variant="primary" size="lg" className="rounded-xl px-7 py-3 shadow-lg shadow-primary/20 hover:shadow-xl hover:shadow-primary/25 border border-primary/20" rightIcon={<ArrowRight className="size-4" />}>
                Start Free Trial
              </Button>
            </Link>
            <Link href={ROUTE_PATHS.login}>
              <Button variant="ghost" size="lg" className="rounded-xl px-7 py-3 border border-border bg-white/40 dark:bg-zinc-900/40 backdrop-blur-xs" leftIcon={<Play className="size-4 fill-current text-zinc-700 dark:text-zinc-300" />}>
                Watch Demo
              </Button>
            </Link>
          </div>

          {/* Brand Logo Ticker (Big Tech Credibility) */}
          <div className="mt-20 opacity-70">
            <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-6">
              Trusted by premium boutique hotels & resorts
            </p>
            <div className="flex flex-wrap justify-center items-center gap-x-12 gap-y-6 text-xs font-bold text-zinc-400 dark:text-zinc-600">
              <span className="flex items-center gap-1.5"><Building2 className="size-4" /> Grand Plaza Resorts</span>
              <span className="flex items-center gap-1.5"><Star className="size-4" /> Legacy Luxury Villas</span>
              <span className="flex items-center gap-1.5"><Sparkles className="size-4" /> Apex Alpine Lodge</span>
              <span className="flex items-center gap-1.5"><Building2 className="size-4" /> Oceanic Retreats</span>
            </div>
          </div>

          {/* macOS Browser Mockup with Aurora shadow */}
          <div className="mt-16 relative max-w-5xl mx-auto group">
            {/* Ambient Backlight */}
            <div className="absolute inset-0 bg-linear-to-r from-primary to-indigo-500 rounded-2xl opacity-10 blur-3xl group-hover:opacity-15 transition duration-500 pointer-events-none" />
            
            {/* macOS Browser Window Frame */}
            <div className="relative rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 p-2 shadow-2xl transition-all duration-300 group-hover:border-primary/20">
              {/* Top Titlebar */}
              <div className="flex items-center justify-between px-3 pb-2 border-b border-zinc-200/60 dark:border-zinc-900 mb-2">
                <div className="flex gap-1.5">
                  <span className="size-3 rounded-full bg-red-400/80 border border-red-500/20" />
                  <span className="size-3 rounded-full bg-yellow-400/80 border border-yellow-500/20" />
                  <span className="size-3 rounded-full bg-green-400/80 border border-green-500/20" />
                </div>
                <div className="flex items-center gap-1 px-4 py-0.5 rounded bg-zinc-200/55 dark:bg-zinc-900 border border-zinc-300/30 text-[9px] text-muted-foreground w-64 justify-center">
                  <ShieldCheck className="size-3 text-emerald-500" /> leadora.novax.com/dashboard
                </div>
                <div className="size-3 opacity-0" />
              </div>
              
              {/* Image Frame */}
              <div className="overflow-hidden rounded-lg border border-zinc-200/50 dark:border-zinc-900">
                <img 
                  src="/image.jpg" 
                  alt="Leadora Dashboard View" 
                  className="w-full h-auto object-cover opacity-95 group-hover:scale-[1.005] transition duration-500" 
                />
              </div>
            </div>
          </div>
        </section>

        {/* Features Section */}
        <section id="features" className="py-24 px-6 max-w-7xl mx-auto border-t border-border/40 relative z-10">
          <div className="text-center mb-20">
            <span className="text-[10px] font-bold text-primary uppercase tracking-widest bg-primary/10 px-3 py-1 rounded-full">System Capabilities</span>
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-zinc-900 dark:text-white mt-4">
              Streamline guest conversion and hotel operations.
            </h2>
            <p className="mt-3 text-sm text-muted-foreground max-w-lg mx-auto font-medium">
              Eliminate friction. Connect follow-up desks, managers, and front office teams in a single, high-fidelity platform.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <Card className="p-8 border border-border bg-white/30 dark:bg-zinc-900/20 backdrop-blur-xs hover:bg-white/80 dark:hover:bg-zinc-900/60 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 group rounded-2xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-24 h-24 bg-linear-to-bl from-primary/5 to-transparent rounded-bl-full pointer-events-none" />
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-6 group-hover:scale-110 transition-transform duration-200">
                <Handshake className="size-5" />
              </div>
              <h3 className="text-base font-bold mb-3">Lead Management</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Capture, track, and score corporate and individual leads. Centralize shift handovers and prevent guest inquiry leakage.
              </p>
            </Card>

            {/* Feature 2 */}
            <Card className="p-8 border border-border bg-white/30 dark:bg-zinc-900/20 backdrop-blur-xs hover:bg-white/80 dark:hover:bg-zinc-900/60 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 group rounded-2xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-24 h-24 bg-linear-to-bl from-primary/5 to-transparent rounded-bl-full pointer-events-none" />
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-6 group-hover:scale-110 transition-transform duration-200">
                <ChartNoAxesCombined className="size-5" />
              </div>
              <h3 className="text-base font-bold mb-3">Sales Pipeline Board</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Drag-and-drop kanban boards to dispatch guest deals, monitor room statuses, and track contract approvals in real-time.
              </p>
            </Card>

            {/* Feature 3 */}
            <Card className="p-8 border border-border bg-white/30 dark:bg-zinc-900/20 backdrop-blur-xs hover:bg-white/80 dark:hover:bg-zinc-900/60 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 group rounded-2xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-24 h-24 bg-linear-to-bl from-primary/5 to-transparent rounded-bl-full pointer-events-none" />
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-6 group-hover:scale-110 transition-transform duration-200">
                <CalendarCheck className="size-5" />
              </div>
              <h3 className="text-base font-bold mb-3">Follow-up Tasks</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Ensure systematic discipline. Assign booking tasks, schedule call reminders, and manage client feedback folders automatically.
              </p>
            </Card>

            {/* Feature 4 */}
            <Card className="p-8 border border-border bg-white/30 dark:bg-zinc-900/20 backdrop-blur-xs hover:bg-white/80 dark:hover:bg-zinc-900/60 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 group rounded-2xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-24 h-24 bg-linear-to-bl from-primary/5 to-transparent rounded-bl-full pointer-events-none" />
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-6 group-hover:scale-110 transition-transform duration-200">
                <Gauge className="size-5" />
              </div>
              <h3 className="text-base font-bold mb-3">SLA Compliance Tracking</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Visual timers track corporate inquiry response rates. Trigger automatic warnings and manager escalations before leads get cold.
              </p>
            </Card>

            {/* Feature 5 */}
            <Card className="p-8 border border-border bg-white/30 dark:bg-zinc-900/20 backdrop-blur-xs hover:bg-white/80 dark:hover:bg-zinc-900/60 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 group rounded-2xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-24 h-24 bg-linear-to-bl from-primary/5 to-transparent rounded-bl-full pointer-events-none" />
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-6 group-hover:scale-110 transition-transform duration-200">
                <FileText className="size-5" />
              </div>
              <h3 className="text-base font-bold mb-3">Quotation Lifecycle</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Draft room rates, send professional proposals, apply manager approval overrides, and convert quotes into reservations.
              </p>
            </Card>

            {/* Feature 6 */}
            <Card className="p-8 border border-border bg-white/30 dark:bg-zinc-900/20 backdrop-blur-xs hover:bg-white/80 dark:hover:bg-zinc-900/60 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 group rounded-2xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-24 h-24 bg-linear-to-bl from-primary/5 to-transparent rounded-bl-full pointer-events-none" />
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-6 group-hover:scale-110 transition-transform duration-200">
                <Clock className="size-5" />
              </div>
              <h3 className="text-base font-bold mb-3">Interaction Timeline</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Maintain a unified guest log of all communications. Document front desk handovers, client concerns, and room updates in order.
              </p>
            </Card>
          </div>
        </section>

        {/* Live Interactive Product Showcase Section (SLA Live Feed Mockup) */}
        <section className="py-24 px-6 max-w-7xl mx-auto border-t border-border/40 relative z-10">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
            {/* Left: Headline info */}
            <div className="lg:col-span-5 space-y-6 text-left">
              <span className="text-[10px] font-bold text-[#1D9E75] uppercase tracking-widest bg-[#1D9E75]/10 px-3 py-1 rounded-full">Interactive Product Highlight</span>
              <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-zinc-900 dark:text-white">
                Guard guest experience with real-time SLAs.
              </h2>
              <p className="text-sm text-muted-foreground leading-relaxed font-medium">
                Leadora monitors every incoming guest quotation and operational task. When timers tick down to critical thresholds, cards color-code automatically and notify supervisors.
              </p>
              
              {/* Tab Selector */}
              <div className="flex flex-col gap-2.5 pt-4">
                <button 
                  onClick={() => setActiveTab("sales")}
                  className={`flex items-center gap-3 p-3 rounded-xl border text-left transition-all ${
                    activeTab === "sales" 
                      ? "border-primary/25 bg-primary/5 text-foreground" 
                      : "border-transparent hover:bg-zinc-100 dark:hover:bg-zinc-900 text-muted-foreground"
                  }`}
                >
                  <div className="size-8 rounded-lg bg-primary/10 flex items-center justify-center text-primary shrink-0">
                    <ChartNoAxesCombined className="size-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold">Booking Quotation Timers</h4>
                    <p className="text-[10px] opacity-75">SLA counts down based on guest VIP status</p>
                  </div>
                </button>

                <button 
                  onClick={() => setActiveTab("ops")}
                  className={`flex items-center gap-3 p-3 rounded-xl border text-left transition-all ${
                    activeTab === "ops" 
                      ? "border-[#1D9E75]/25 bg-[#1D9E75]/5 text-foreground" 
                      : "border-transparent hover:bg-zinc-100 dark:hover:bg-zinc-900 text-muted-foreground"
                  }`}
                >
                  <div className="size-8 rounded-lg bg-[#1D9E75]/10 flex items-center justify-center text-[#1D9E75] shrink-0">
                    <Clock className="size-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold">Front Shift Handovers</h4>
                    <p className="text-[10px] opacity-75">Ensures key operational requests never lapse</p>
                  </div>
                </button>
              </div>
            </div>

            {/* Right: Live Interactive CSS Widget Mockup */}
            <div className="lg:col-span-7 bg-zinc-100/50 dark:bg-zinc-950/30 border border-zinc-200 dark:border-zinc-850 p-6 sm:p-8 rounded-2xl relative shadow-xl backdrop-blur-xs flex flex-col gap-4">
              <div className="absolute top-4 right-4 text-[9px] font-bold text-muted-foreground uppercase bg-zinc-200/50 dark:bg-zinc-900 px-2 py-0.5 rounded">
                Live Feed Mockup
              </div>

              {activeTab === "sales" ? (
                <>
                  {/* Mock Lead 1: Normal SLA */}
                  <div className="p-4 rounded-xl border border-zinc-200 bg-white dark:border-zinc-900 dark:bg-zinc-950 flex items-center justify-between shadow-xs hover:-translate-y-px transition duration-200">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold">Inquiry #3940 - Suite Booking</span>
                        <span className="text-[9px] font-bold bg-zinc-100 dark:bg-zinc-900 px-1.5 py-0.5 rounded text-zinc-500">Corporate</span>
                      </div>
                      <p className="text-[10px] text-muted-foreground">Guest: Robert Chen (Deloitte Tech)</p>
                    </div>
                    <div className="text-right space-y-1">
                      <div className="text-xs font-mono font-bold text-emerald-500">01h 14m remaining</div>
                      <span className="text-[9px] font-bold bg-emerald-50 dark:bg-emerald-950/30 text-emerald-600 px-2 py-0.5 rounded">SLA Safe</span>
                    </div>
                  </div>

                  {/* Mock Lead 2: Critical SLA (Glowing alert) */}
                  <div className="p-4 rounded-xl border border-red-200 bg-rose-50/10 dark:border-red-950/30 dark:bg-rose-950/5 flex items-center justify-between shadow-xs animate-pulse hover:-translate-y-px transition duration-200">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-rose-900 dark:text-rose-100">Inquiry #3942 - Wedding Gala Block</span>
                        <span className="text-[9px] font-bold bg-rose-100 dark:bg-rose-950/50 px-1.5 py-0.5 rounded text-rose-500">VIP</span>
                      </div>
                      <p className="text-[10px] text-rose-700/80 dark:text-rose-300/80">Guest: Sarah Jenkins (Planner)</p>
                    </div>
                    <div className="text-right space-y-1">
                      <div className="text-xs font-mono font-bold text-red-500">00h 08m left</div>
                      <span className="text-[9px] font-bold bg-red-100 dark:bg-red-950/50 text-red-500 px-2 py-0.5 rounded border border-red-200/40">Critical SLA</span>
                    </div>
                  </div>
                </>
              ) : (
                <>
                  {/* Mock Op 1: Normal SLA */}
                  <div className="p-4 rounded-xl border border-zinc-200 bg-white dark:border-zinc-900 dark:bg-zinc-950 flex items-center justify-between shadow-xs hover:-translate-y-px transition duration-200">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold">Shift Handover - Room 405 AC leak</span>
                        <span className="text-[9px] font-bold bg-zinc-100 dark:bg-zinc-900 px-1.5 py-0.5 rounded text-zinc-500">Front Desk</span>
                      </div>
                      <p className="text-[10px] text-muted-foreground">Assignee: Maintenance Staff (Night Shift)</p>
                    </div>
                    <div className="text-right space-y-1">
                      <div className="text-xs font-mono font-bold text-zinc-400">Assigned 12m ago</div>
                      <span className="text-[9px] font-bold bg-zinc-100 dark:bg-zinc-900 text-zinc-500 px-2 py-0.5 rounded">In Progress</span>
                    </div>
                  </div>

                  {/* Mock Op 2: Overdue SLA */}
                  <div className="p-4 rounded-xl border border-amber-250 bg-amber-50/10 dark:border-amber-900/30 dark:bg-amber-950/5 flex items-center justify-between shadow-xs hover:-translate-y-px transition duration-200">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-amber-800 dark:text-amber-100">Airport Pickup - Guest flight landed</span>
                        <span className="text-[9px] font-bold bg-amber-100 dark:bg-amber-950/50 px-1.5 py-0.5 rounded text-amber-500">Shuttle</span>
                      </div>
                      <p className="text-[10px] text-amber-700/80 dark:text-amber-300/80">Guest: Mr. Tanaka (CEO Toyota Group)</p>
                    </div>
                    <div className="text-right space-y-1">
                      <div className="text-xs font-mono font-bold text-amber-500">Overdue by 15m</div>
                      <span className="text-[9px] font-bold bg-amber-100 dark:bg-amber-950/50 text-amber-500 px-2 py-0.5 rounded">Escalated</span>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </section>

        {/* Stats / Performance Section */}
        <section id="stats" className="py-20 bg-zinc-50 dark:bg-zinc-950/40 border-y border-border/40 relative z-10">
          <div className="max-w-7xl mx-auto px-6 grid grid-cols-1 md:grid-cols-3 gap-12 text-center">
            {/* Stat Item 1 */}
            <div className="space-y-3 relative group">
              <div className="flex justify-center">
                <div className="size-11 rounded-xl bg-emerald-500/10 flex items-center justify-center text-emerald-500 group-hover:scale-110 transition duration-200">
                  <TrendingUp className="size-5" />
                </div>
              </div>
              <p className="text-4xl font-black text-zinc-900 dark:text-white">148.250 ₫</p>
              <p className="text-xs font-bold text-emerald-500 mt-1 flex items-center justify-center gap-1">
                +12.4% average weekly sales volume
              </p>
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">Pipeline Value Logged</p>
            </div>

            {/* Stat Item 2 */}
            <div className="space-y-3 relative group">
              <div className="flex justify-center">
                <div className="size-11 rounded-xl bg-emerald-500/10 flex items-center justify-center text-emerald-500 group-hover:scale-110 transition duration-200">
                  <CheckCircle2 className="size-5" />
                </div>
              </div>
              <p className="text-4xl font-black text-zinc-900 dark:text-white">99.8%</p>
              <p className="text-xs font-bold text-emerald-500 mt-1 flex items-center justify-center gap-1">
                Response Target Compliance
              </p>
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">Corporate SLA Rating</p>
            </div>

            {/* Stat Item 3 */}
            <div className="space-y-3 relative group">
              <div className="flex justify-center">
                <div className="size-11 rounded-xl bg-emerald-500/10 flex items-center justify-center text-emerald-500 group-hover:scale-110 transition duration-200">
                  <Star className="size-5" />
                </div>
              </div>
              <p className="text-4xl font-black text-zinc-900 dark:text-white">+24.5%</p>
              <p className="text-xs font-bold text-emerald-500 mt-1 flex items-center justify-center gap-1">
                Average Booking Increase
              </p>
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">Direct Channel Conversion</p>
            </div>
          </div>
        </section>

        {/* Benefits & Mobile App Section */}
        <section id="benefits" className="py-24 px-6 max-w-7xl mx-auto relative z-10">
          <div className="text-center mb-20">
            <span className="text-[10px] font-bold text-indigo-500 uppercase tracking-widest bg-indigo-500/10 px-3 py-1 rounded-full">Operational Harmony</span>
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-zinc-900 dark:text-white mt-4">
              Integrated CRM for every hotel role.
            </h2>
            <p className="mt-3 text-sm text-muted-foreground max-w-lg mx-auto font-medium">
              Designed from the ground up to fit the fast pace of luxury hotel staff.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-10">
            <div className="space-y-4">
              <div className="size-11 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
                <LayoutDashboard className="size-5" />
              </div>
              <h3 className="text-base font-bold">Centralized CRM Hub</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Connect reservation desks, sales managers, and front office shifts to a single data repository. No more scattered Excel sheets or missed guest requests.
              </p>
            </div>

            <div className="space-y-4">
              <div className="size-11 rounded-xl bg-[#1D9E75]/10 flex items-center justify-center text-[#1D9E75]">
                <Gauge className="size-5" />
              </div>
              <h3 className="text-base font-bold">Real-time SLA Guards</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Keep response standards high. Timers warn agents when response deadlines approach, escalating unresolved tasks before booking intent cools.
              </p>
            </div>

            <div className="space-y-4">
              <div className="size-11 rounded-xl bg-indigo-500/10 flex items-center justify-center text-indigo-500">
                <Smartphone className="size-5" />
              </div>
              <h3 className="text-base font-bold">Companion Mobile App</h3>
              <p className="text-xs text-muted-foreground leading-relaxed font-medium">
                Empower your sales agents with a native mobile companion app. Perform quick lead creations, update deal statuses, and log meeting details on the move.
              </p>
            </div>
          </div>
        </section>

        {/* Brand New Modern FAQ Grid Section */}
        <section className="py-24 px-6 max-w-5xl mx-auto border-t border-border/40 relative z-10">
          <div className="text-center mb-16">
            <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest bg-zinc-150 dark:bg-zinc-900 px-3 py-1 rounded-full">FAQ</span>
            <h2 className="text-2xl md:text-3xl font-extrabold tracking-tight text-zinc-900 dark:text-white mt-4">
              Frequently Asked Questions
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 text-left">
            <div>
              <h4 className="text-xs font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-1.5"><ShieldCheck className="size-4 text-primary" /> Is Leadora secure?</h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed mt-2.5 font-medium">
                Absolutely. Leadora is secured using industry-standard SSL, bcrypt password hashing, and integrated Supabase authentication with field-level audit logs for tracking database modifications.
              </p>
            </div>
            <div>
              <h4 className="text-xs font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-1.5"><Smartphone className="size-4 text-primary" /> Can we access it on mobile devices?</h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed mt-2.5 font-medium">
                Yes! We have a native companion mobile application developed in Flutter. It syncs directly with the Spring Boot backend via custom JSON REST APIs.
              </p>
            </div>
            <div>
              <h4 className="text-xs font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-1.5"><CheckCircle2 className="size-4 text-primary" /> How long does onboarding take?</h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed mt-2.5 font-medium">
                Your CRM workspace is initialized instantly. You can easily import guest lists, corporate coordinators, and existing room packages within hours.
              </p>
            </div>
            <div>
              <h4 className="text-xs font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-1.5"><Star className="size-4 text-primary" /> Does it support multi-hotel organizations?</h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed mt-2.5 font-medium">
                Yes, our database architecture supports role-based scopes (FO, Manager, Admin) capable of isolating or aggregating lead pipelines across different hotels.
              </p>
            </div>
          </div>
        </section>

        {/* Vercel-like Dark Mesh CTA Section */}
        <section className="py-16 px-6 max-w-5xl mx-auto relative z-10 mb-10">
          <div className="relative rounded-3xl overflow-hidden border border-zinc-200 dark:border-zinc-800/80 bg-zinc-950 p-8 sm:p-12 text-center shadow-2xl">
            {/* Mesh gradient circles */}
            <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[70%] h-[70%] bg-linear-to-b from-primary/20 via-indigo-500/5 to-transparent rounded-full blur-3xl pointer-events-none" />

            <div className="relative z-10 max-w-xl mx-auto space-y-6">
              <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight text-white">
                Boost your direct sales today.
              </h2>
              <p className="text-xs sm:text-sm text-zinc-400 leading-relaxed font-medium">
                Join high-performing hospitality teams using Leadora to track pipelines, protect client response SLAs, and secure room revenue.
              </p>
              
              <div className="pt-4 flex flex-wrap justify-center gap-4">
                <Link href={ROUTE_PATHS.login}>
                  <Button variant="primary" size="md" className="rounded-xl shadow-lg border border-primary/25" rightIcon={<ArrowUpRight className="size-4" />}>
                    Get Started Instantly
                  </Button>
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>

      <LandingFooter />
    </div>
  );
}
