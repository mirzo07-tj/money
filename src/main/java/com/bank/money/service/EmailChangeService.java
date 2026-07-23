package com.bank.money.service;

import com.bank.money.entity.EmailChangeToken;
import com.bank.money.entity.User;
import com.bank.money.exception.InvalidTokenException;
import com.bank.money.repository.EmailChangeTokenRepository;
import com.bank.money.repository.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChangeService {

    private final UserRepository userRepository;
    private final EmailChangeTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.reset-password.token-ttl-minutes}")
    private int tokenTtlMinutes;

    @Transactional
    public void requestEmailChange(Long userId, String newEmail, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }

        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalStateException("Это уже ваш текущий email");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalStateException("Этот email уже используется другим пользователем");
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        EmailChangeToken token = new EmailChangeToken();
        token.setUser(user);
        token.setNewEmail(newEmail);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes));
        tokenRepository.save(token);

        emailService.sendEmailChangeConfirmation(newEmail, rawToken);

        log.info("Email change requested for user {}", userId);
    }

    @Transactional
    public void confirmEmailChange(String rawToken) {
        String tokenHash = hashToken(rawToken);

        EmailChangeToken token = tokenRepository
                .findByTokenHashAndUsedFalseAndExpiresAtAfter(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new InvalidTokenException("Ссылка недействительна или срок её действия истёк"));

        User user = token.getUser();
        user.setEmail(token.getNewEmail());
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Email changed successfully for user {}", user.getId());
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void requestEmailChange(String name, @NotBlank @Email String newEmail, @NotBlank String currentPassword) {
    }
}