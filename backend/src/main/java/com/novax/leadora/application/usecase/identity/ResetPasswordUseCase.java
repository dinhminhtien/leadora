package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.infrastructure.persistence.entity.PasswordResetTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.PasswordResetTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void execute(String token, String newPassword) {
        PasswordResetTokenEntity resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalStateException("Invalid token."));

        if (resetToken.isUsed()) {
            throw new IllegalStateException("Token has already been used.");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token has expired.");
        }

        // Validate password complexity
        validatePasswordComplexity(newPassword);

        // Update password hash
        UserEntity user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalStateException("Password must be at least 6 characters.");
        }
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));

        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSymbol) {
            throw new IllegalStateException("Password must contain at least one lowercase letter, one uppercase letter, one digit, and one symbol.");
        }
    }
}
