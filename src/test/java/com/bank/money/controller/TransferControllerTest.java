package com.bank.money.controller;

import com.bank.money.config.SecurityConfig;
import com.bank.money.dto.CreateTransferRequest;
import com.bank.money.dto.P2PTransferRequest;
import com.bank.money.dto.TransferResponse;
import com.bank.money.entity.Currency;
import com.bank.money.entity.TransferType;
import com.bank.money.security.*;
import com.bank.money.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link TransferController}.
 */
@WebMvcTest(TransferController.class)
@Import(SecurityConfig.class)
class TransferControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TransferService transferService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private JwtAccessDeniedHandler jwtAccessDeniedHandler;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;
    @MockitoBean private CorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // ─── Фабрика ответа ───────────────────────────────────────────────────────
    private TransferResponse sampleTransfer(Long id) {
        return new TransferResponse(id, "ACC_FROM", "ACC_TO",
                BigDecimal.valueOf(100), Currency.USD,
                "Тест", Instant.now(), false, null, null);
    }

    // =========================================================================
    // POST /api/transfers/p2p
    // =========================================================================
    @Nested
    @DisplayName("POST /api/transfers/p2p — P2P перевод")
    class P2PTransferTests {

        @Test
        @WithMockUser(username = "alice")
        @DisplayName("Валидный P2P запрос → 201 Created")
        void shouldReturn201ForValidP2P() throws Exception {
            P2PTransferRequest req = new P2PTransferRequest();
            req.setFromAccountId(1L);
            req.setToAccountNumber("ACC_TO");
            req.setAmount(BigDecimal.valueOf(500));
            req.setDescription("Оплата");

            when(transferService.transferToOtherUser(eq("alice"), any())).thenReturn(sampleTransfer(10L));

            mockMvc.perform(post("/api/transfers/p2p")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.fromAccountNumber").value("ACC_FROM"))
                    .andExpect(jsonPath("$.toAccountNumber").value("ACC_TO"));
        }

        @Test
        @WithMockUser
        @DisplayName("fromAccountId = null → 400 Bad Request")
        void shouldReturn400WhenFromAccountIdNull() throws Exception {
            String body = """
                    {"toAccountNumber": "ACC_TO", "amount": 100}
                    """;
            mockMvc.perform(post("/api/transfers/p2p")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("amount < 0.01 → 400 Bad Request")
        void shouldReturn400WhenAmountTooSmall() throws Exception {
            P2PTransferRequest req = new P2PTransferRequest();
            req.setFromAccountId(1L);
            req.setToAccountNumber("ACC_TO");
            req.setAmount(BigDecimal.valueOf(0.001));

            mockMvc.perform(post("/api/transfers/p2p")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("amount > 1 000 000 → 400 Bad Request")
        void shouldReturn400WhenAmountTooLarge() throws Exception {
            P2PTransferRequest req = new P2PTransferRequest();
            req.setFromAccountId(1L);
            req.setToAccountNumber("ACC_TO");
            req.setAmount(BigDecimal.valueOf(2_000_000));

            mockMvc.perform(post("/api/transfers/p2p")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Без аутентификации → 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            String body = """
                    {"fromAccountId":1,"toAccountNumber":"ACC","amount":100}
                    """;
            mockMvc.perform(post("/api/transfers/p2p")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // POST /api/transfers/own
    // =========================================================================
    @Nested
    @DisplayName("POST /api/transfers/own — перевод между своими счетами")
    class OwnTransferTests {

        @Test
        @WithMockUser(username = "alice")
        @DisplayName("Валидный перевод → 201 Created")
        void shouldReturn201ForValidOwnTransfer() throws Exception {
            String body = """
                    {"fromAccountId": 1, "toAccountId": 2, "amount": 200.00, "description": "Перекидываю"}
                    """;
            when(transferService.transferBetweenOwnAccounts(eq("alice"), any())).thenReturn(sampleTransfer(20L));

            mockMvc.perform(post("/api/transfers/own")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(20));
        }
    }

    // =========================================================================
    // GET /api/transfers/me
    // =========================================================================
    @Nested
    @DisplayName("GET /api/transfers/me — история переводов пользователя")
    class MyHistoryTests {

        @Test
        @WithMockUser(username = "alice")
        @DisplayName("Возвращает страницу → 200 OK")
        void shouldReturn200WithPage() throws Exception {
            Page<TransferResponse> page = new PageImpl<>(List.of(sampleTransfer(1L), sampleTransfer(2L)));
            when(transferService.getMyHistory(eq("alice"), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/transfers/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(1));
        }

        @Test
        @WithMockUser(username = "alice")
        @DisplayName("Фильтр по type=P2P — параметр передаётся в сервис")
        void shouldPassTypeFilterToService() throws Exception {
            Page<TransferResponse> page = new PageImpl<>(List.of(sampleTransfer(3L)));
            when(transferService.getMyHistory(eq("alice"), isNull(), isNull(), eq(TransferType.P2P_TRANSFER), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/transfers/me").param("type", "P2P"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(3));
        }

        @Test
        @DisplayName("Без аутентификации → 401")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/transfers/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // GET /api/transfers/accounts/{accountId}/history
    // =========================================================================
    @Nested
    @DisplayName("GET /api/transfers/accounts/{accountId}/history — история счёта")
    class AccountHistoryTests {

        @Test
        @WithMockUser(username = "alice")
        @DisplayName("Возвращает страницу истории счёта → 200 OK")
        void shouldReturn200WithAccountHistory() throws Exception {
            Page<TransferResponse> page = new PageImpl<>(List.of(sampleTransfer(5L)));
            when(transferService.getAccountHistory(eq("alice"), eq(7L), isNull(), isNull(), isNull(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/transfers/accounts/7/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(5));
        }

        @Test
        @DisplayName("Без аутентификации → 401")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/transfers/accounts/1/history"))
                    .andExpect(status().isUnauthorized());
        }
    }
}