package com.bank.money.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для {@link JwtAuthenticationEntryPoint} и {@link JwtAccessDeniedHandler}.
 *
 * Проверяем JSON-ответы на 401 Unauthorized и 403 Forbidden:
 * - HTTP статус код
 * - Content-Type: application/json
 * - Тело: {status, message, path}
 */
@ExtendWith(MockitoExtension.class)
class JwtErrorHandlersTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===================== JwtAuthenticationEntryPoint (401) =====================

    @Nested
    @DisplayName("JwtAuthenticationEntryPoint (401 Unauthorized)")
    class EntryPointTests {

        private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();

        @Test
        @DisplayName("Должен установить статус 401")
        void shouldSetStatus401() throws IOException {
            setupMocks("/api/accounts");
            AuthenticationException authEx = mockAuthException("Bad credentials");
            ByteArrayOutputStream outputStream = setupOutputStream();

            entryPoint.commence(request, response, authEx);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("Content-Type = application/json")
        void shouldSetJsonContentType() throws IOException {
            setupMocks("/api/accounts");
            AuthenticationException authEx = mockAuthException("Bad credentials");
            ByteArrayOutputStream outputStream = setupOutputStream();

            entryPoint.commence(request, response, authEx);

            verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        }

        @Test
        @DisplayName("Тело ответа содержит status=401, message, path")
        void shouldReturnCorrectJsonBody() throws IOException {
            setupMocks("/api/protected");
            AuthenticationException authEx = mockAuthException("Token expired");
            ByteArrayOutputStream outputStream = setupOutputStream();

            entryPoint.commence(request, response, authEx);

            Map<String, Object> body = objectMapper.readValue(
                    outputStream.toByteArray(), Map.class);

            assertEquals(401, body.get("status"));
            assertEquals("Требуется авторизация", body.get("message"));
            assertEquals("/api/protected", body.get("path"));
        }
    }

    // ===================== JwtAccessDeniedHandler (403) =====================

    @Nested
    @DisplayName("JwtAccessDeniedHandler (403 Forbidden)")
    class AccessDeniedTests {

        private final JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler();

        @Test
        @DisplayName("Должен установить статус 403")
        void shouldSetStatus403() throws IOException {
            setupMocks("/api/admin/users");
            AccessDeniedException ex = new AccessDeniedException("Access denied");
            setupOutputStream();

            handler.handle(request, response, ex);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        @DisplayName("Content-Type = application/json")
        void shouldSetJsonContentType() throws IOException {
            setupMocks("/api/admin/users");
            AccessDeniedException ex = new AccessDeniedException("Access denied");
            setupOutputStream();

            handler.handle(request, response, ex);

            verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        }

        @Test
        @DisplayName("Тело ответа содержит status=403, message, path")
        void shouldReturnCorrectJsonBody() throws IOException {
            setupMocks("/api/admin/delete");
            AccessDeniedException ex = new AccessDeniedException("Forbidden");
            ByteArrayOutputStream outputStream = setupOutputStream();

            handler.handle(request, response, ex);

            Map<String, Object> body = objectMapper.readValue(
                    outputStream.toByteArray(), Map.class);

            assertEquals(403, body.get("status"));
            assertEquals("Доступ запрещён", body.get("message"));
            assertEquals("/api/admin/delete", body.get("path"));
        }
    }

    // ===================== Утилиты =====================

    private void setupMocks(String requestUri) {
        when(request.getRequestURI()).thenReturn(requestUri);
    }

    private ByteArrayOutputStream setupOutputStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = new DelegatingServletOutputStream(baos);
        when(response.getOutputStream()).thenReturn(servletOutputStream);
        return baos;
    }

    private AuthenticationException mockAuthException(String message) {
        return new AuthenticationException(message) {};
    }

    /**
     * Простая обёртка, чтобы ObjectMapper мог писать в ByteArrayOutputStream
     * через ServletOutputStream.
     */
    private static class DelegatingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream delegate;

        DelegatingServletOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            // Не нужен в unit-тестах
        }
    }
}
