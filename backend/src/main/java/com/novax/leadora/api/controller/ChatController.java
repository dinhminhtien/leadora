package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateChatSessionRequest;
import com.novax.leadora.api.dto.request.RenameChatSessionRequest;
import com.novax.leadora.api.dto.request.SendChatMessageRequest;
import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.api.dto.response.ChatSessionResponse;
import com.novax.leadora.api.dto.response.SendMessageResponse;
import com.novax.leadora.application.usecase.chat.CreateChatSessionUseCase;
import com.novax.leadora.application.usecase.chat.DeleteChatSessionUseCase;
import com.novax.leadora.application.usecase.chat.GetChatMessagesUseCase;
import com.novax.leadora.application.usecase.chat.GetChatSessionsUseCase;
import com.novax.leadora.application.usecase.chat.RenameChatSessionUseCase;
import com.novax.leadora.application.usecase.chat.SendChatMessageUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Internal Sales Role-Based Chat Assistant.
 *
 * <p>The acting user is resolved from the {@code X-User-Id} header (temporary, until login/RBAC
 * is finished). For now every authenticated actor has top scope — they can ask anything within
 * the business domain; mutation and off-topic requests are refused by the guardrail (BR-35).
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class ChatController {

    private final CurrentUserProvider currentUserProvider;
    private final CreateChatSessionUseCase createChatSessionUseCase;
    private final GetChatSessionsUseCase getChatSessionsUseCase;
    private final GetChatMessagesUseCase getChatMessagesUseCase;
    private final SendChatMessageUseCase sendChatMessageUseCase;
    private final RenameChatSessionUseCase renameChatSessionUseCase;
    private final DeleteChatSessionUseCase deleteChatSessionUseCase;

    /** Create New Chat Session. */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody(required = false) CreateChatSessionRequest request) {
        UserEntity user = currentUserProvider.resolve(userId);
        String title = request != null ? request.getTitle() : null;
        ChatSessionResponse session = createChatSessionUseCase.execute(user, title);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(session, "Chat session created"));
    }

    /** View Chat Session List. */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> listSessions(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UserEntity user = currentUserProvider.resolve(userId);
        return ResponseEntity.ok(ApiResponse.success(getChatSessionsUseCase.execute(user)));
    }

    /** Continue Existing Chat Session — load its messages. */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID sessionId) {
        UserEntity user = currentUserProvider.resolve(userId);
        return ResponseEntity.ok(ApiResponse.success(getChatMessagesUseCase.execute(sessionId, user)));
    }

    /** Send a message — Query Assigned / Team CRM Data via the assistant. */
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<SendMessageResponse>> sendMessage(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendChatMessageRequest request) {
        UserEntity user = currentUserProvider.resolve(userId);
        SendMessageResponse response =
                sendChatMessageUseCase.execute(sessionId, user, request.getContent());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Rename Chat Session. */
    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> renameSession(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody RenameChatSessionRequest request) {
        UserEntity user = currentUserProvider.resolve(userId);
        ChatSessionResponse session =
                renameChatSessionUseCase.execute(sessionId, user, request.getTitle());
        return ResponseEntity.ok(ApiResponse.success(session, "Chat session renamed"));
    }

    /** Delete Chat Session (soft delete). */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID sessionId) {
        UserEntity user = currentUserProvider.resolve(userId);
        deleteChatSessionUseCase.execute(sessionId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Chat session deleted"));
    }
}
