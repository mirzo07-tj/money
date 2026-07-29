package com.bank.money.controller;

import com.bank.money.config.SecurityConfig;
import com.bank.money.dto.*;
import com.bank.money.security.JwtAccessDeniedHandler;
import com.bank.money.security.JwtAuthenticationEntryPoint;
import com.bank.money.security.JwtAuthenticationFilter;
import com.bank.money.security.JwtTokenProvider;
import com.bank.money.service.AuthService;
import com.bank.money.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link AuthController}.
 * @Import(SecurityConfig.class) обязателен, чтобы реально применялись
 * правила permitAll для /api/auth/** и т.д., а не дефолтная политика
 * "всё требует аутентификации" от авто-конфигурации @WebMvcTest.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Моки сервисов ───────────────────────────────────────────────────────
    @MockitoBean private UserService userService;
    @MockitoBean private AuthService authService;

    // ─── Моки security-инфраструктуры ────────────────────────────────────────
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private JwtAccessDeniedHandler jwtAccessDeniedHandler;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private CorsConfigurationSource corsConfigurationSource;

    // Прозрачный фильтр — на уровне класса, чтобы применяться ко ВСЕМ @Nested
    // классам ниже (JUnit 5 наследует @BeforeEach внешнего класса).
    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // =========================================================================
    // POST /api/auth/register
    // =========================================================================
    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Валидный запрос → 201 Created + тело UserResponse")
        void shouldReturn201OnValidRegister() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john_doe");
            req.setEmail("john@example.com");
            req.setPassword("Secret1pass");

            UserResponse resp = new UserResponse(1L, "john_doe", "john@example.com",
                    Instant.now(), Set.of("USER"));
            when(userService.register(any(RegisterRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("john_doe"))
                    .andExpect(jsonPath("$.email").value("john@example.com"))
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Пустой username → 400 Bad Request")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("");
            req.setEmail("john@example.com");
            req.setPassword("Secret1pass");

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Некорректный email → 400 Bad Request")
        void shouldReturn400WhenEmailInvalid() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john_doe");
            req.setEmail("not-an-email");
            req.setPassword("Secret1pass");

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Слабый пароль (без цифры) → 400 Bad Request")
        void shouldReturn400WhenPasswordTooWeak() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john_doe");
            req.setEmail("john@example.com");
            req.setPassword("weakpassword"); // нет цифр и заглавных

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // POST /api/auth/login
    // =========================================================================
    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Валидные данные → 200 OK + accessToken")
        void shouldReturn200OnValidLogin() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setUsername("john_doe");
            req.setPassword("Secret1pass");

            LoginResponse resp = new LoginResponse("access.jwt.token", "refresh-uuid", "john_doe");
            when(authService.login(any(LoginRequest.class), anyString())).thenReturn(resp);

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.jwt.token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-uuid"))
                    .andExpect(jsonPath("$.type").value("Bearer"))
                    .andExpect(jsonPath("$.username").value("john_doe"));
        }

        @Test
        @DisplayName("Пустой пароль → 400 Bad Request")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setUsername("john_doe");
            req.setPassword("");

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-Forwarded-For передаётся в сервис как IP")
        void shouldExtractIpFromXForwardedFor() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setUsername("john_doe");
            req.setPassword("Secret1pass");

            LoginResponse resp = new LoginResponse("tok", "ref", "john_doe");
            when(authService.login(any(), anyString())).thenReturn(resp);

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            // Проверяем что сервис вызван с первым IP из X-Forwarded-For
            verify(authService).login(any(LoginRequest.class), eq("1.2.3.4"));
        }
    }

    // =========================================================================
    // POST /api/auth/verify-email
    // =========================================================================
    @Nested
    @DisplayName("POST /api/auth/verify-email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Валидный токен → 204 No Content")
        void shouldReturn204OnValidToken() throws Exception {
            String body = """
                    {"token": "valid-verification-token"}
                    """;

            doNothing().when(userService).verifyEmail(anyString());

            mockMvc.perform(post("/api/auth/verify-email")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            verify(userService).verifyEmail("valid-verification-token");
        }
    }

    // =========================================================================
    // PUT /api/auth/change-password  (требует аутентификации)
    // =========================================================================
    @Nested
    @DisplayName("PUT /api/auth/change-password")
    class ChangePasswordTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Авторизованный пользователь → 204 No Content")
        void shouldReturn204ForAuthenticatedUser() throws Exception {
            String body = """
                    {
                        "currentPassword": "OldPass1",
                        "newPassword": "NewPass2",
                        "confirmPassword": "NewPass2"
                    }
                    """;
            doNothing().when(userService).changePassword(anyString(), any());

            mockMvc.perform(put("/api/auth/change-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Неавторизованный запрос → 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(put("/api/auth/change-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\",\"confirmPassword\":\"b\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // POST /api/auth/refresh
    // =========================================================================
    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("Валидный refresh token → 200 OK + новый accessToken")
        void shouldReturn200OnValidRefresh() throws Exception {
            String body = "{\"refreshToken\": \"old-refresh-uuid\"}";
            LoginResponse resp = new LoginResponse("new.access.token", "new-refresh-uuid", "john_doe");
            when(authService.refresh("old-refresh-uuid")).thenReturn(resp);

            mockMvc.perform(post("/api/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new.access.token"));
        }
    }

    // =========================================================================
    // POST /api/auth/logout
    // =========================================================================
    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Валидный refresh token → 204 No Content")
        void shouldReturn204OnLogout() throws Exception {
            String body = "{\"refreshToken\": \"some-refresh-token\"}";
            doNothing().when(authService).logout(anyString());

            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            verify(authService).logout("some-refresh-token");
        }
    }
}