package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;

import java.util.UUID;

/**
 * The acting user, flattened out of the JPA entity.
 *
 * <p>{@code UserEntity} is a managed entity whose {@code role} association is lazy, so it can only
 * be read on the thread and inside the transaction that loaded it. The chat pipeline does neither:
 * context is gathered on a worker thread and the LLM call runs with no transaction open, both so a
 * three-second model call cannot pin a database connection. Passing this record instead of the
 * entity makes that safe by construction rather than by remembering not to touch a getter.
 *
 * <p>Built at the request boundary, where the entity is still attached.
 */
public record ChatActor(UUID userId, String fullName, String roleName) {

    public static ChatActor from(UserEntity user) {
        String role = (user.getRole() != null && user.getRole().getRoleName() != null)
                ? user.getRole().getRoleName() : "";
        return new ChatActor(user.getUserId(), user.getFullName(), role);
    }
}
