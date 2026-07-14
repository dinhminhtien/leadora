"use client";

import React, { useEffect } from "react";
import { Sparkles, ShieldCheck, MessageSquareHeart, FileSearch, MousePointerClick } from "lucide-react";
import { useChatStore } from "@/stores/chat_store";
import { LiaMascot } from "@/features/ai_assistant/components/LiaMascot";
import { Button } from "@/components/ui/Button";

const FEATURES = [
  {
    Icon: MessageSquareHeart,
    title: "Ask about your data",
    desc: "Leads, deals, tasks, revenue — Lia looks them up directly from the data you're allowed to see.",
  },
  {
    Icon: FileSearch,
    title: "Search company documents",
    desc: "Policies, procedures and guidelines are answered from the documents that have been ingested (RAG).",
  },
  {
    Icon: ShieldCheck,
    title: "Read-only and safe",
    desc: "Lia never creates, edits or deletes anything — every action request is declined.",
  },
];

export function LiaLandingScreen() {
  const { openAssistant, isOpen } = useChatStore();

  // Opening this page brings Lia up automatically.
  useEffect(() => {
    openAssistant();
  }, [openAssistant]);

  return (
    <div className="mx-auto flex max-w-3xl flex-col items-center px-4 py-10 text-center">
      <div className="relative">
        <div className="absolute -inset-6 -z-10 rounded-full bg-primary/10 blur-2xl" />
        <LiaMascot variant="full" size={180} />
      </div>

      <h1 className="mt-4 flex items-center gap-2 text-2xl font-extrabold text-primary">
        <Sparkles className="size-5 text-primary" />
        Hi, I&apos;m Lia
      </h1>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">
        Your lead-care &amp; follow-up assistant. I float in the corner of the screen and{" "}
        <span className="font-semibold text-primary">can be dragged anywhere</span> — click me to start
        chatting on any page.
      </p>

      <Button
        onClick={openAssistant}
        variant="primary"
        size="lg"
        className="mt-5 rounded-full"
        leftIcon={<MousePointerClick className="size-4" />}
      >
        {isOpen ? "Lia is open in the corner" : "Open the Lia assistant"}
      </Button>

      <div className="mt-10 grid w-full gap-3 sm:grid-cols-3">
        {FEATURES.map(({ Icon, title, desc }) => (
          <div
            key={title}
            className="rounded-2xl border border-border bg-background p-4 text-left shadow-sm"
          >
            <div className="mb-2 flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Icon className="size-4.5" />
            </div>
            <p className="text-sm font-bold text-foreground">{title}</p>
            <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{desc}</p>
          </div>
        ))}
      </div>

      <p className="mt-8 text-[11px] text-muted-foreground">
        Tip: drag the Lia icon wherever you like — the position is remembered for next time.
      </p>
    </div>
  );
}
