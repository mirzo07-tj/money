package com.bank.money.service;

import com.bank.money.dto.LoginRequest;
import com.bank.money.dto.LoginResponse;
import com.bank.money.entity.RefreshToken;
import com.bank.money.entity.User;
import com.bank.money.repository.RefreshTokenRepository;
import com.bank.money.repository.UserRepository;
import com.bank.money.security.IpLoginAttemptService;
import com.bank.money.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final IpLoginAttemptService ipLoginAttemptService;

    public LoginResponse login(LoginRequest request, String ipAddress) {
        log.info("Попытка входа username={} ip={}", request.getUsername(), ipAddress);

        ipLoginAttemptService.checkAllowed(ipAddress);

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            log.warn("Попытка входа в заблокированный аккаунт username={}", request.getUsername());
            throw new LockedException("Аккаунт временно заблокирован из-за превышения количества попыток входа. Попробуйте позже.");
        }

        if (user != null && !user.isEmailVerified()) {
            log.warn("Попытка входа с неподтверждённым email username={}", request.getUsername());
            throw new LockedException("Email не подтверждён. Проверьте почту и перейдите по ссылке из письма.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            if (user != null && (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null)) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
            ipLoginAttemptService.registerSuccess(ipAddress);

            User authenticatedUser = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Пользователь не найден после аутентификации"));

            String accessToken = jwtTokenProvider.generateAccessToken(authenticatedUser.getUsername());
            String refreshToken = createRefreshToken(authenticatedUser);

            log.info("Успешный вход username={}", request.getUsername());
            return new LoginResponse(accessToken, refreshToken, authenticatedUser.getUsername());

        } catch (BadCredentialsException ex) {
            ipLoginAttemptService.registerFailure(ipAddress);

            if (user != null) {
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);

                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
                    log.warn("Аккаунт заблокирован после {} неудачных попыток username={}", attempts, request.getUsername());
                }
                userRepository.save(user);
            }

            throw ex;
        }
    }

    public LoginResponse refresh(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token не найден"));

        if (storedToken.isRevoked()) {
            log.warn("Попытка использовать отозванный refresh token, userId={}", storedToken.getUser().getId());
            throw new IllegalArgumentException("Refresh token отозван");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Refresh token истёк, userId={}", storedToken.getUser().getId());
            throw new IllegalArgumentException("Refresh token истёк");
        }

        User user = storedToken.getUser();

        // Ротация: старый токен отзываем, выдаём новый
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
        String newRefreshToken = createRefreshToken(user);

        log.info("Токены обновлены для username={}", user.getUsername());
        return new LoginResponse(newAccessToken, newRefreshToken, user.getUsername());
    }

    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Refresh token отозван для userId={}", token.getUser().getId());
        });
    }

    private String createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(jwtTokenProvider.generateRefreshTokenValue());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshExpirationMs()));
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }
}