package com.bank.money.controller;

import com.bank.money.config.SecurityConfig;
import com.bank.money.dto.UpdateUserRolesRequest;
import com.bank.money.dto.UserResponse;
import com.bank.money.security.JwtAccessDeniedHandler;
import com.bank.money.security.JwtAuthenticationEntryPoint;
import com.bank.money.security.JwtAuthenticationFilter;
import com.bank.money.security.JwtTokenProvider;
import com.bank.money.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link AdminController}.
 *
 * ВАЖНО: @Import(SecurityConfig.class) обязателен, иначе @WebMvcTest не
 * поднимает @EnableMethodSecurity, и @PreAuthorize("hasRole('ADMIN')")
 * на контроллере просто не будет применяться — обычный USER пройдёт
 * как будто он ADMIN.
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserService userService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    // Требуются конструктором SecurityConfig, который теперь реально
    // поднимается в контексте через @Import:
    @MockitoBean private JwtAccessDeniedHandler jwtAccessDeniedHandler;
    @MockitoBean private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private UserResponse sampleUser(Set<String> roles) {
        return new UserResponse(1L, "john_doe", "john@example.com", Instant.now(), roles);
    }

    // =========================================================================
    // PATCH /api/admin/users/{id}/roles
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/roles — изменить роли пользователя")
    class UpdateUserRolesTests {

        @Test
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        @DisplayName("ADMIN: валидный запрос → 200 OK + обновлённые роли")
        void shouldReturn200ForAdmin() throws Exception {
            UpdateUserRolesRequest req = new UpdateUserRolesRequest();
            req.setRoles(Set.of("USER", "ADMIN"));

            when(userService.updateUserRoles(eq(1L), any())).thenReturn(sampleUser(Set.of("USER", "ADMIN")));

            mockMvc.perform(patch("/api/admin/users/1/roles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("john_doe"));
        }

        @Test
        @WithMockUser(username = "user", roles = {"USER"})
        @DisplayName("Обычный USER → 403 Forbidden")
        void shouldReturn403ForRegularUser() throws Exception {
            UpdateUserRolesRequest req = new UpdateUserRolesRequest();
            req.setRoles(Set.of("ADMIN"));

            mockMvc.perform(patch("/api/admin/users/1/roles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @TestConfiguration
        static class TestConfig {

            @Bean
            CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.addAllowedOrigin("*");
                configuration.addAllowedMethod("*");
                configuration.addAllowedHeader("*");

                UrlBasedCorsConfigurationSource source =
                        new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration("/**", configuration);

                return source;
            }
        }
        @Test
        @DisplayName("Без аутентификации → 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(patch("/api/admin/users/1/roles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roles\":[\"USER\"]}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Пустой набор ролей → 400 Bad Request (валидация @NotEmpty)")
        void shouldReturn400ForEmptyRoles() throws Exception {
            // @NotEmpty на UpdateUserRolesRequest.roles запрещает пустой список
            String body = "{\"roles\":[]}";
            mockMvc.perform(patch("/api/admin/users/5/roles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).updateUserRoles(anyLong(), any());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Другой userId — сервис вызван с корректным id")
        void shouldCallServiceWithCorrectUserId() throws Exception {
            when(userService.updateUserRoles(eq(99L), any())).thenReturn(sampleUser(Set.of("USER")));

            String body = "{\"roles\":[\"USER\"]}";
            mockMvc.perform(patch("/api/admin/users/99/roles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            verify(userService).updateUserRoles(eq(99L), any());
        }
    }
}