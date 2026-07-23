package com.bank.money.service;

import com.bank.money.dto.ChangePasswordRequest;
import com.bank.money.dto.RegisterRequest;
import com.bank.money.dto.UserResponse;
import com.bank.money.entity.EmailVerificationToken;
import com.bank.money.entity.Role;
import com.bank.money.entity.User;
import com.bank.money.repository.EmailVerificationTokenRepository;
import com.bank.money.repository.RefreshTokenRepository;
import com.bank.money.repository.RoleRepository;
import com.bank.money.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    public UserResponse register(RegisterRequest request) {
        log.info("Попытка регистрации пользователя username={}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Регистрация отклонена: username={} уже занят", request.getUsername());
            throw new IllegalArgumentException("Пользователь с таким username уже существует");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Регистрация отклонена: email={} уже используется", request.getEmail());
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Роль USER не найдена в базе"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);
        log.info("Пользователь зарегистрирован, id={}, роль=USER", saved.getId());

        sendVerificationToken(saved);

        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getCreatedAt(), Set.of("USER"));
    }

    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt(), roleNames);
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            log.warn("Неверный текущий пароль при смене пароля username={}", username);
            throw new IllegalArgumentException("Текущий пароль указан неверно");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Новый пароль должен отличаться от текущего");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUser(user);

        log.info("Пароль успешно изменён username={}, все refresh-токены отозваны", username);
    }

    public UserResponse updateUserRoles(Long userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: id=" + userId));

        Set<Role> newRoles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Роль не найдена: " + name)))
                .collect(Collectors.toSet());

        user.setRoles(newRoles);
        User saved = userRepository.save(user);

        log.info("Роли пользователя id={} обновлены на: {}", userId, roleNames);

        Set<String> updatedRoleNames = saved.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getCreatedAt(), updatedRoleNames);
    }

    private void sendVerificationToken(User user) {
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        verificationTokenRepository.save(token);

        emailService.sendVerificationEmail(user.getEmail(), rawToken);

        log.info("Письмо для подтверждения email отправлено userId={}", user.getId());
    }

    public void verifyEmail(String rawToken) {
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = verificationTokenRepository
                .findByTokenHashAndUsedFalseAndExpiresAtAfter(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("Ссылка недействительна или срок её действия истёк"));

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        verificationTokenRepository.save(token);

        log.info("Email подтверждён userId={}", user.getId());
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
}