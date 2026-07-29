package com.bank.money.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для {@link JwtTokenProvider}.
 *
 * Без Spring-контекста: поля secret/expirationMs устанавливаются через reflection,
 * чтобы тестировать чистую логику генерации и валидации JWT.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // Тестовый секрет — минимум 32 байта для HMAC-SHA256
    private static final String TEST_SECRET = "my-super-secret-key-for-testing-32bytes!!";
    private static final long ACCESS_EXPIRATION_MS = 60_000; // 1 минута
    private static final long REFRESH_EXPIRATION_MS = 604_800_000; // 7 дней

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();
        setField(jwtTokenProvider, "secret", TEST_SECRET);
        setField(jwtTokenProvider, "accessExpirationMs", ACCESS_EXPIRATION_MS);
        setField(jwtTokenProvider, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
    }

    // ===================== Генерация Access Token =====================

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("Должен вернуть непустой токен")
        void shouldReturnNonEmptyToken() {
            String token = jwtTokenProvider.generateAccessToken("testuser");

            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("Токен должен содержать 3 части (header.payload.signature)")
        void shouldHaveThreeParts() {
            String token = jwtTokenProvider.generateAccessToken("testuser");

            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT должен состоять из 3 частей");
        }

        @Test
        @DisplayName("Для разных пользователей — разные токены")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            String token1 = jwtTokenProvider.generateAccessToken("alice");
            String token2 = jwtTokenProvider.generateAccessToken("bob");

            assertNotEquals(token1, token2);
        }
    }

    // ===================== Извлечение username =====================

    @Nested
    @DisplayName("getUsernameFromToken()")
    class GetUsernameFromTokenTests {

        @Test
        @DisplayName("Должен извлечь правильный username из токена")
        void shouldExtractCorrectUsername() {
            String token = jwtTokenProvider.generateAccessToken("john_doe");

            String username = jwtTokenProvider.getUsernameFromToken(token);

            assertEquals("john_doe", username);
        }

        @Test
        @DisplayName("Username с кириллицей должен корректно сохраняться")
        void shouldHandleCyrillicUsername() {
            String token = jwtTokenProvider.generateAccessToken("пользователь");

            String username = jwtTokenProvider.getUsernameFromToken(token);

            assertEquals("пользователь", username);
        }

        @Test
        @DisplayName("Username с email-форматом должен корректно сохраняться")
        void shouldHandleEmailLikeUsername() {
            String token = jwtTokenProvider.generateAccessToken("user@example.com");

            String username = jwtTokenProvider.getUsernameFromToken(token);

            assertEquals("user@example.com", username);
        }
    }

    // ===================== Валидация токена =====================

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("Валидный токен → true")
        void shouldReturnTrueForValidToken() {
            String token = jwtTokenProvider.generateAccessToken("testuser");

            assertTrue(jwtTokenProvider.validateToken(token));
        }

        @Test
        @DisplayName("Истёкший токен → false")
        void shouldReturnFalseForExpiredToken() throws Exception {
            // Устанавливаем expiration = 0 мс (токен истекает мгновенно)
            setField(jwtTokenProvider, "accessExpirationMs", 0L);

            String token = jwtTokenProvider.generateAccessToken("testuser");

            // Небольшая задержка чтобы токен точно истёк
            Thread.sleep(10);

            assertFalse(jwtTokenProvider.validateToken(token));
        }

        @Test
        @DisplayName("Повреждённый токен (мусорная строка) → false")
        void shouldReturnFalseForGarbageToken() {
            assertFalse(jwtTokenProvider.validateToken("this.is.not.a.jwt"));
        }

        @Test
        @DisplayName("Пустая строка → false")
        void shouldReturnFalseForEmptyString() {
            assertFalse(jwtTokenProvider.validateToken(""));
        }

        @Test
        @DisplayName("null → false")
        void shouldReturnFalseForNull() {
            assertFalse(jwtTokenProvider.validateToken(null));
        }

        @Test
        @DisplayName("Токен, подписанный другим ключом → false")
        void shouldReturnFalseForTokenSignedWithDifferentKey() {
            // Генерируем токен другим ключом
            SecretKey anotherKey = Keys.hmacShaKeyFor(
                    "another-completely-different-secret-key!!!".getBytes()
            );

            String foreignToken = Jwts.builder()
                    .subject("hacker")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(anotherKey)
                    .compact();

            assertFalse(jwtTokenProvider.validateToken(foreignToken));
        }

        @Test
        @DisplayName("Токен с изменённым payload (tampered) → false")
        void shouldReturnFalseForTamperedToken() {
            String validToken = jwtTokenProvider.generateAccessToken("testuser");

            // Разбиваем токен и меняем один символ в payload
            String[] parts = validToken.split("\\.");
            char[] payload = parts[1].toCharArray();
            payload[0] = (payload[0] == 'a') ? 'b' : 'a';
            String tamperedToken = parts[0] + "." + new String(payload) + "." + parts[2];

            assertFalse(jwtTokenProvider.validateToken(tamperedToken));
        }
    }

    // ===================== Refresh Token =====================

    @Nested
    @DisplayName("generateRefreshTokenValue()")
    class GenerateRefreshTokenValueTests {

        @Test
        @DisplayName("Должен вернуть непустую строку UUID-формата")
        void shouldReturnNonEmptyUuid() {
            String refreshToken = jwtTokenProvider.generateRefreshTokenValue();

            assertNotNull(refreshToken);
            assertFalse(refreshToken.isBlank());
            // UUID формат: 8-4-4-4-12 hex символов
            assertTrue(refreshToken.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
            ));
        }

        @Test
        @DisplayName("Два вызова дают разные значения (уникальность)")
        void shouldGenerateUniqueValues() {
            String token1 = jwtTokenProvider.generateRefreshTokenValue();
            String token2 = jwtTokenProvider.generateRefreshTokenValue();

            assertNotEquals(token1, token2);
        }
    }

    // ===================== getRefreshExpirationMs =====================

    @Test
    @DisplayName("getRefreshExpirationMs() должен вернуть значение из конфигурации")
    void shouldReturnConfiguredRefreshExpiration() {
        assertEquals(REFRESH_EXPIRATION_MS, jwtTokenProvider.getRefreshExpirationMs());
    }

    // ===================== Утилита для reflection =====================

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
