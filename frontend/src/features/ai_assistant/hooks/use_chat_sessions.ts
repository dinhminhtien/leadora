"use client";

import { useQuery } from "@tanstack/react-query";

import { chatAssistantService } from "@/services/chat_assistant_service";

export function useChatSessions() {
  return useQuery({
    queryKey: ["chat-sessions"],
    queryFn: () => chatAssistantService.getSessions(),
  });
}
