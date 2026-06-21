package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.PasswordResetTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.PasswordResetTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.infrastructure.integration.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public void execute(String email) {
        String trimmedEmail = email.trim();
        UserEntity user = userRepository.findWithRoleByEmailIgnoreCase(trimmedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", trimmedEmail));

        // Generate secure 15-minute token
        String token = UUID.randomUUID().toString();
        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + token;

        // Send the real HTML email via SMTP
        emailService.sendResetPasswordHtmlEmail(user.getEmail(), resetLink);

        // Standard developer console log with link (simulates email sending for local debug visibility)
        log.info("\n==================================================" +
                 "\n[PASSWORD RESET EMAIL SENT]" +
                 "\nTo: " + user.getEmail() +
                 "\nSubject: Password Reset Request" +
                 "\nLink: " + resetLink +
                 "\n==================================================");
    }
}
