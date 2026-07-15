package com.bank.money.service;

import com.bank.money.dto.LoginRequest;
import com.bank.money.dto.LoginResponse;
import com.bank.money.entity.RefreshToken;
import com.bank.money.entity.User;
import com.bank.money.repository.RefreshTokenRepository;
import com.bank.money.repository.UserRepository;
import com.bank.money.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        log.info("Попытка входа username={}", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден после аутентификации"));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = createRefreshToken(user);

        log.info("Успешный вход username={}", request.getUsername());
        return new LoginResponse(accessToken, refreshToken, user.getUsername());
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