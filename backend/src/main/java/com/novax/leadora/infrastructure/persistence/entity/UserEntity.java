package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone", length = 15)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Last successful sign-in (email or OAuth). Drives the 7-day idle → INACTIVE job. */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;
}
