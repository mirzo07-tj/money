package com.bank.money.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для {@link JwtAuthenticationFilter}.
 *
 * Проверяем цепочку фильтра:
 * - Корректный Bearer токен → аутентификация в SecurityContext
 * - Невалидный токен → SecurityContext пуст
 * - Нет заголовка → пропуск
 * - Ошибка при загрузке UserDetails → clearContext
 *
 * ВАЖНО: фильтр вызывает request.getRequestURI() сразу после проверки
 * заголовка Bearer (для skip-логики swagger/api-docs/auth), ещё до
 * валидации токена. Поэтому в любом тесте, где заголовок начинается
 * с "Bearer ", обязательно нужно застабить getRequestURI() —
 * иначе мок вернёт null и упадёт NPE на path.startsWith(...).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===================== Успешная аутентификация =====================

    @Nested
    @DisplayName("Успешный Bearer токен")
    class SuccessfulAuthTests {

        @Test
        @DisplayName("Валидный токен → аутентификация установлена в SecurityContext")
        void shouldSetAuthenticationForValidToken() throws ServletException, IOException {
            String token = "valid.jwt.token";
            UserDetails userDetails = User.builder()
                    .username("john")
                    .password("pw")
                    .authorities("ROLE_USER")
                    .build();

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getRequestURI()).thenReturn("/api/accounts");
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("john");
            when(userDetailsService.loadUserByUsername("john")).thenReturn(userDetails);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth, "Authentication должен быть установлен");
            assertEquals("john", auth.getName());
            assertTrue(auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        }

        @Test
        @DisplayName("Фильтр должен всегда вызвать filterChain.doFilter()")
        void shouldAlwaysCallFilterChain() throws ServletException, IOException {
            String token = "valid.jwt.token";
            UserDetails userDetails = User.builder()
                    .username("john")
                    .password("pw")
                    .authorities("ROLE_USER")
                    .build();

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getRequestURI()).thenReturn("/api/accounts");
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("john");
            when(userDetailsService.loadUserByUsername("john")).thenReturn(userDetails);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("Credentials = null (не хранит пароль в контексте)")
        void shouldHaveNullCredentials() throws ServletException, IOException {
            String token = "valid.jwt.token";
            UserDetails userDetails = User.builder()
                    .username("john")
                    .password("pw")
                    .authorities("ROLE_USER")
                    .build();

            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(request.getRequestURI()).thenReturn("/api/accounts");
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("john");
            when(userDetailsService.loadUserByUsername("john")).thenReturn(userDetails);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNull(auth.getCredentials(), "Credentials должны быть null");
        }
    }

    // ===================== Нет заголовка или неверный формат =====================

    @Nested
    @DisplayName("Нет или неверный Authorization заголовок")
    class NoOrInvalidHeaderTests {

        @Test
        @DisplayName("Нет заголовка Authorization → пропуск, SecurityContext пуст")
        void shouldSkipWhenNoHeader() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Заголовок без 'Bearer ' → пропуск")
        void shouldSkipWhenNotBearerPrefix() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Пустой Authorization заголовок → пропуск")
        void shouldSkipWhenEmptyHeader() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }
    }

    // ===================== Невалидный токен =====================

    @Nested
    @DisplayName("Невалидный токен")
    class InvalidTokenTests {

        @Test
        @DisplayName("validateToken() = false → SecurityContext остаётся пустым")
        void shouldNotSetAuthWhenTokenInvalid() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token");
            when(request.getRequestURI()).thenReturn("/api/accounts");
            when(jwtTokenProvider.validateToken("invalid.token")).thenReturn(false);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Невалидный токен — не вызывает getUsernameFromToken()")
        void shouldNotExtractUsernameForInvalidToken() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(request.getRequestURI()).thenReturn("/api/accounts");
            when(jwtTokenProvider.validateToken("bad.token")).thenReturn(false);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
        }
    }

    // ===================== Ошибка при загрузке UserDetails =====================

    @Nested
    @DisplayName("Ошибка при загрузке UserDetails")
    class UserDetailsErrorTests {

        @Test
        @DisplayName("Исключение в loadUserByUsername → clearContext, filterChain продолжает")
        void shouldClearContextAndContinueOnError() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");
            when(request.getRequestURI()).thenReturn("/api/accounts");
            when(jwtTokenProvider.validateToken("valid.token")).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken("valid.token")).thenReturn("john");
            when(userDetailsService.loadUserByUsername("john"))
                    .thenThrow(new RuntimeException("DB is down"));

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }
    }
}
