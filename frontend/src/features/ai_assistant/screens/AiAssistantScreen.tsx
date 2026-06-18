"use client";

import React, { useState } from "react";
import { Bot, Sparkles, Send, User, ChevronRight, CheckCircle2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";

type Message = {
  id: string;
  sender: "user" | "copilot";
  text: string;
  timestamp: string;
};

function createMessage(sender: "user" | "copilot", text: string): Message {
  return {
    id: `${sender === "user" ? "u" : "c"}-${Date.now()}`,
    sender,
    text,
    timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
  };
}

export function AiAssistantScreen() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "m1",
      sender: "copilot",
      text: "Hello! I am your Leadora AI Copilot. I can draft emails, analyze deals value, check pending tasks, or summarize guest interaction timelines. How can I help you close more bookings today?",
      timestamp: "10:30 AM"
    }
  ]);
  const [inputVal, setInputVal] = useState("");

  const handleSend = (textToSend?: string) => {
    const queryText = textToSend || inputVal;
    if (!queryText.trim()) return;

    // User Message
    const userMsg = createMessage("user", queryText);

    setMessages(prev => [...prev, userMsg]);
    if (!textToSend) setInputVal("");

    // AI Response Mock Logic
    setTimeout(() => {
      let aiText = "I have analyzed the current deals and CRM logs. Please let me know how to proceed.";
      const queryLower = queryText.toLowerCase();

      if (queryLower.includes("email") || queryLower.includes("draft")) {
        aiText = `Here is a drafted follow-up email for your guest:
        
Subject: Direct Update: Your Event Booking Enquiry at Leadora Resort

Dear Customer,
Thank you for choosing Leadora Resort for your upcoming group event. We have saved your preferred dates on hold and would love to arrange a site visit this week to finalize room block estimates and banquet menu preferences. Let me know if tomorrow at 2:00 PM works for a brief walk-through!

Warm regards,
John Doe
Leadora Sales Team`;
      } else if (queryLower.includes("revenue") || queryLower.includes("forecast") || queryLower.includes("q2")) {
        aiText = "Based on our current sales pipeline: We have **5 active deals** totaling **$99,000** in value. The weighted revenue forecast is **$48,700**, considering the win probabilities of each stage. Direct Website is our top inquiry channel.";
      } else if (queryLower.includes("sla") || queryLower.includes("alert")) {
        aiText = "We currently have **1 active warning** regarding response speed. The SLA compliance rate is at **91.8%**, which is well above our system target threshold of 90.0%.";
      }

      const copilotMsg = createMessage("copilot", aiText);
      setMessages(prev => [...prev, copilotMsg]);
    }, 800);
  };

  const handleSuggestionClick = (prompt: string) => {
    handleSend(prompt);
  };

  return (
    <div className="space-y-6 flex flex-col h-[calc(100vh-140px)]">
      {/* Header */}
      <div className="flex justify-between items-center shrink-0">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <Sparkles className="size-5 text-blue-600 animate-pulse" />
            AI Sales Copilot
          </h1>
          <p className="text-xs text-slate-400">Ask questions, draft emails, summarize active pipeline deals or verify response SLA metrics</p>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-emerald-600 font-semibold bg-emerald-50 px-2 py-1 rounded">
          <CheckCircle2 className="size-3.5" /> Core LLM Online
        </div>
      </div>

      <div className="flex-1 flex flex-col lg:flex-row gap-6 min-h-0">
        {/* Chat Feed Column */}
        <div className="flex-1 flex flex-col bg-white border border-slate-100 rounded-2xl shadow-sm min-h-0 overflow-hidden">
          {/* Messages container */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4 custom-scrollbar">
            {messages.map(msg => (
              <div
                key={msg.id}
                className={`flex gap-3 max-w-[85%] ${
                  msg.sender === "user" ? "ml-auto flex-row-reverse" : ""
                }`}
              >
                {/* Avatar */}
                <div
                  className={`size-8 rounded-full shrink-0 flex items-center justify-center font-bold text-xs ${
                    msg.sender === "user"
                      ? "bg-slate-200 text-slate-700"
                      : "bg-blue-600 text-white"
                  }`}
                >
                  {msg.sender === "user" ? <User className="size-4" /> : <Bot className="size-4" />}
                </div>

                {/* Message Bubble */}
                <div className="space-y-1">
                  <div
                    className={`rounded-2xl px-4 py-2.5 text-xs whitespace-pre-line leading-relaxed ${
                      msg.sender === "user"
                        ? "bg-blue-600 text-white font-medium"
                        : "bg-slate-50 text-slate-700 border border-slate-100"
                    }`}
                  >
                    {msg.text}
                  </div>
                  <div
                    className={`text-[9px] text-slate-400 font-semibold ${
                      msg.sender === "user" ? "text-right" : ""
                    }`}
                  >
                    {msg.timestamp}
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Form message input */}
          <div className="border-t border-slate-100 p-3 bg-slate-50/50 shrink-0">
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Ask Copilot: 'Draft follow-up email' or 'Forecast Q2 sales revenue'..."
                value={inputVal}
                onChange={e => setInputVal(e.target.value)}
                onKeyDown={e => e.key === "Enter" && handleSend()}
                className="flex-1 rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-blue-500 transition"
              />
              <Button
                onClick={() => handleSend()}
                className="bg-blue-600 hover:bg-blue-700 rounded-xl size-9 flex items-center justify-center text-white font-semibold transition shrink-0"
              >
                <Send className="size-4" />
              </Button>
            </div>
          </div>
        </div>

        {/* Suggestion Column */}
        <div className="w-full lg:w-72 space-y-4 shrink-0 flex flex-col justify-between">
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-bold text-slate-800 uppercase tracking-wider">Suggested Actions</CardTitle>
              <CardDescription className="text-[10px] text-slate-400">Click to instantly dispatch commands</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              {[
                "Draft email for Miller Wedding",
                "Summarize Q2 sales revenue forecast",
                "Check active warning response SLAs"
              ].map((prompt, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSuggestionClick(prompt)}
                  className="w-full text-left p-2.5 rounded-lg border border-slate-100 bg-slate-50 text-[11px] font-semibold text-slate-600 hover:bg-blue-50 hover:text-blue-700 hover:border-blue-200 transition flex items-center justify-between group"
                >
                  <span>{prompt}</span>
                  <ChevronRight className="size-3 text-slate-400 group-hover:text-blue-500 transition" />
                </button>
              ))}
            </CardContent>
          </Card>

          {/* Guidelines */}
          <Card className="border-slate-100 shadow-sm bg-slate-900 text-slate-300">
            <CardContent className="p-4 space-y-2 text-[10px] leading-relaxed">
              <p className="font-bold text-white uppercase tracking-wider">Copilot Guidelines</p>
              <p>Copilot reads current leads, active won/lost statuses, and schedules notifications to automate daily admin tasks.</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
