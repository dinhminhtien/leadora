package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.infrastructure.persistence.entity.enums.ChatRole;

/**
 * One prior turn of a conversation, detached from JPA so it can be replayed to the model outside
 * the transaction that read it.
 */
public record ChatTurn(ChatRole role, String content) {

    public boolean isUser() {
        return role == ChatRole.USER;
    }
}
