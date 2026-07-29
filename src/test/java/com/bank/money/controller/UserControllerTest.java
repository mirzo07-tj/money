package com.bank.money.controller;

import com.bank.money.dto.UserResponse;
import com.bank.money.security.JwtAuthenticationEntryPoint;
import com.bank.money.security.JwtAuthenticationFilter;
import com.bank.money.security.JwtTokenProvider;
import com.bank.money.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link UserController}.
 *
 * ВАЖНО: JwtAuthenticationFilter здесь заменён Mockito-моком
 * (@MockitoBean), но реальный фильтр всё равно стоит в цепочке
 * Spring Security. По умолчанию мок-фильтр ничего не делает и НЕ
 * вызывает filterChain.doFilter(...), поэтому запрос никогда не
 * доходит до контроллера (ответ 200 с пустым телом, сервис не
 * вызывается). Чтобы мок вёл себя как "прозрачный" фильтр, нужно
 * явно заставить его прокидывать запрос дальше по цепочке.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserService userService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @BeforeEach
    void setUpFilterPassthrough() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // =========================================================================
    // GET /api/users/me
    // =========================================================================

    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "john_doe")
    @DisplayName("GET /api/users/me — авторизованный → 200 + данные пользователя")
    void shouldReturn200WithCurrentUser() throws Exception {
        UserResponse resp = new UserResponse(1L, "john_doe", "john@example.com",
                Instant.parse("2025-01-01T00:00:00Z"), Set.of("USER"));
        when(userService.getCurrentUser("john_doe")).thenReturn(resp);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @WithMockUser(username = "john_doe")
    @DisplayName("GET /api/users/me — сервис вызывается ровно 1 раз с правильным именем")
    void shouldCallServiceOnce() throws Exception {
        when(userService.getCurrentUser("john_doe"))
                .thenReturn(new UserResponse(1L, "john_doe", "j@e.com", Instant.now(), Set.of()));

        mockMvc.perform(get("/api/users/me")).andExpect(status().isOk());

        verify(userService, times(1)).getCurrentUser("john_doe");
    }

    @Test
    @DisplayName("GET /api/users/me — без токена → 401")
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
