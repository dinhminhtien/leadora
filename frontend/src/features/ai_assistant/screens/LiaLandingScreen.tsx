"use client";

import React, { useEffect } from "react";
import { Sparkles, ShieldCheck, MessageSquareHeart, FileSearch, MousePointerClick } from "lucide-react";
import { useChatStore } from "@/stores/chat_store";
import { LiaMascot } from "@/features/ai_assistant/components/LiaMascot";

const FEATURES = [
  {
    Icon: MessageSquareHeart,
    title: "Hỏi về dữ liệu của bạn",
    desc: "Lead, deal, task, doanh số — Lia tra cứu trực tiếp từ dữ liệu bạn được phép xem.",
  },
  {
    Icon: FileSearch,
    title: "Tra cứu tài liệu công ty",
    desc: "Nội quy, chính sách, quy trình… được trả lời theo tài liệu đã nạp (RAG).",
  },
  {
    Icon: ShieldCheck,
    title: "Chỉ đọc, an toàn",
    desc: "Lia không bao giờ tạo/sửa/xoá dữ liệu — mọi yêu cầu thao tác đều bị từ chối.",
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
        <div className="absolute -inset-6 -z-10 rounded-full bg-teal-100/50 blur-2xl" />
        <LiaMascot variant="full" size={180} />
      </div>

      <h1 className="mt-4 flex items-center gap-2 text-2xl font-extrabold text-teal-700">
        <Sparkles className="size-5 text-pink-400" />
        Chào bạn, mình là Lia
      </h1>
      <p className="mt-1 max-w-md text-sm text-slate-500">
        Trợ lý chăm sóc &amp; follow-up lead của bạn. Mình nổi ở góc màn hình và{" "}
        <span className="font-semibold text-teal-600">kéo-thả được tới bất kỳ đâu</span> — bấm vào
        mình để bắt đầu trò chuyện trên mọi trang.
      </p>

      <button
        onClick={openAssistant}
        className="mt-5 inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-teal-400 to-teal-500 px-5 py-2.5 text-sm font-bold text-white shadow-lg shadow-teal-500/25 transition hover:scale-105"
      >
        <MousePointerClick className="size-4" />
        {isOpen ? "Lia đang mở ở góc màn hình" : "Mở trợ lý Lia"}
      </button>

      <div className="mt-10 grid w-full gap-3 sm:grid-cols-3">
        {FEATURES.map(({ Icon, title, desc }) => (
          <div
            key={title}
            className="rounded-2xl border border-teal-100 bg-white p-4 text-left shadow-sm"
          >
            <div className="mb-2 flex size-9 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
              <Icon className="size-4.5" />
            </div>
            <p className="text-sm font-bold text-slate-800">{title}</p>
            <p className="mt-1 text-xs leading-relaxed text-slate-500">{desc}</p>
          </div>
        ))}
      </div>

      <p className="mt-8 text-[11px] text-slate-400">
        Mẹo: kéo biểu tượng Lia tới vị trí bạn thích — vị trí sẽ được ghi nhớ cho lần sau.
      </p>
    </div>
  );
}
