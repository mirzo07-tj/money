package com.bank.money.controller;

import com.bank.money.config.SecurityConfig;
import com.bank.money.security.JwtAccessDeniedHandler;
import com.bank.money.security.JwtAuthenticationEntryPoint;
import com.bank.money.security.JwtAuthenticationFilter;
import com.bank.money.security.JwtTokenProvider;
import com.bank.money.service.PasswordResetService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link PasswordResetController}.
 * Маршруты /auth/forgot-password и /auth/reset-password — публичные
 * (permitAll в SecurityConfig).
 */
@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
class PasswordResetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PasswordResetService passwordResetService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private JwtAccessDeniedHandler jwtAccessDeniedHandler;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private CorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // =========================================================================
    // POST /auth/forgot-password
    // =========================================================================
    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Валидный username → 200 + сообщение (не раскрываем, существует ли аккаунт)")
        void shouldReturn200WithMessage() throws Exception {
            doNothing().when(passwordResetService).requestReset("john_doe");

            String body = "{\"username\": \"john_doe\"}";

            mockMvc.perform(post("/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists());

            verify(passwordResetService).requestReset("john_doe");
        }

        @Test
        @DisplayName("Несуществующий пользователь → всё равно 200 (anti-enumeration)")
        void shouldReturn200EvenForUnknownUser() throws Exception {
            // Сервис не кидает исключение — это намеренное поведение
            doNothing().when(passwordResetService).requestReset("ghost_user");

            String body = "{\"username\": \"ghost_user\"}";

            mockMvc.perform(post("/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Пустой username → 400 Bad Request")
        void shouldReturn400WhenUsernameBlank() throws Exception {
            String body = "{\"username\": \"\"}";

            mockMvc.perform(post("/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Endpoint публичный — без токена всё равно 200")
        void shouldBePublicEndpoint() throws Exception {
            doNothing().when(passwordResetService).requestReset(anyString());

            // Запрос без @WithMockUser, без JWT — должен пройти
            mockMvc.perform(post("/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"anyone\"}"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // POST /auth/reset-password
    // =========================================================================
    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Валидный токен и пароль → 200 + сообщение об успехе")
        void shouldReturn200OnValidReset() throws Exception {
            String body = """
                    {
                        "token": "valid-reset-token",
                        "newPassword": "NewPass123"
                    }
                    """;
            doNothing().when(passwordResetService).resetPassword(anyString(), anyString());

            mockMvc.perform(post("/auth/reset-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Пароль успешно изменён"));

            verify(passwordResetService).resetPassword("valid-reset-token", "NewPass123");
        }

        @Test
        @DisplayName("Пустой token → 400 Bad Request")
        void shouldReturn400WhenTokenBlank() throws Exception {
            String body = """
                    {"token": "", "newPassword": "NewPass123"}
                    """;
            mockMvc.perform(post("/auth/reset-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Слабый пароль → 400 Bad Request")
        void shouldReturn400WhenPasswordTooWeak() throws Exception {
            String body = """
                    {"token": "some-token", "newPassword": "123"}
                    """;
            mockMvc.perform(post("/auth/reset-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}