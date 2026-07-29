package com.bank.money.controller;

import com.bank.money.config.SecurityConfig;
import com.bank.money.security.JwtAccessDeniedHandler;
import com.bank.money.security.JwtAuthenticationEntryPoint;
import com.bank.money.security.JwtAuthenticationFilter;
import com.bank.money.security.JwtTokenProvider;
import com.bank.money.service.EmailChangeService;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link EmailChangeController}.
 */
@WebMvcTest(EmailChangeController.class)
@Import(SecurityConfig.class)
class EmailChangeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmailChangeService emailChangeService;
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
    // POST /api/users/me/change-email
    // =========================================================================
    @Nested
    @DisplayName("POST /api/users/me/change-email")
    class ChangeEmailTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Валидный запрос → 200 + сообщение")
        void shouldReturn200ForValidRequest() throws Exception {
            String body = """
                    {
                        "newEmail": "newemail@example.com",
                        "currentPassword": "Secret1pass"
                    }
                    """;
            doNothing().when(emailChangeService)
                    .requestEmailChange("john_doe", "newemail@example.com", "Secret1pass");

            mockMvc.perform(post("/api/users/me/change-email")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists());

            verify(emailChangeService).requestEmailChange("john_doe", "newemail@example.com", "Secret1pass");
        }

        @Test
        @WithMockUser
        @DisplayName("Некорректный email → 400 Bad Request")
        void shouldReturn400WhenNewEmailInvalid() throws Exception {
            String body = """
                    {
                        "newEmail": "not-valid-email",
                        "currentPassword": "Secret1pass"
                    }
                    """;
            mockMvc.perform(post("/api/users/me/change-email")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("Пустой пароль → 400 Bad Request")
        void shouldReturn400WhenPasswordBlank() throws Exception {
            String body = """
                    {
                        "newEmail": "valid@example.com",
                        "currentPassword": ""
                    }
                    """;
            mockMvc.perform(post("/api/users/me/change-email")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Без аутентификации → 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            String body = """
                    {"newEmail": "x@example.com", "currentPassword": "Pass123"}
                    """;
            mockMvc.perform(post("/api/users/me/change-email")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // POST /api/users/confirm-email-change
    // =========================================================================
    @Nested
    @DisplayName("POST /api/users/confirm-email-change")
    class ConfirmEmailChangeTests {

        @Test
        @DisplayName("Валидный токен → 200 + сообщение об успехе")
        void shouldReturn200WithSuccessMessage() throws Exception {
            String body = "{\"token\": \"confirm-token-xyz\"}";
            doNothing().when(emailChangeService).confirmEmailChange("confirm-token-xyz");

            mockMvc.perform(post("/api/users/confirm-email-change")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Email успешно изменён"));

            verify(emailChangeService).confirmEmailChange("confirm-token-xyz");
        }

        @Test
        @DisplayName("Пустой токен → 400 Bad Request")
        void shouldReturn400WhenTokenBlank() throws Exception {
            String body = "{\"token\": \"\"}";

            mockMvc.perform(post("/api/users/confirm-email-change")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}