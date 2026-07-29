package com.bank.money.controller;

import com.bank.money.config.SecurityConfig;
import com.bank.money.dto.*;
import com.bank.money.entity.AccountStatus;
import com.bank.money.entity.Currency;
import com.bank.money.security.JwtAccessDeniedHandler;
import com.bank.money.security.JwtAuthenticationEntryPoint;
import com.bank.money.security.JwtAuthenticationFilter;
import com.bank.money.security.JwtTokenProvider;
import com.bank.money.service.BankAccountService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тесты для {@link BankAccountController}.
 */
@WebMvcTest(BankAccountController.class)
@Import(SecurityConfig.class)
class BankAccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private BankAccountService bankAccountService;
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

    // ─── Фабрика ответа ───────────────────────────────────────────────────────
    private BankAccountResponse sampleAccount(Long id) {
        return new BankAccountResponse(id, "ACC" + id, BigDecimal.valueOf(5000),
                Currency.USD, AccountStatus.ACTIVE, Instant.now());
    }

    // =========================================================================
    // POST /api/accounts
    // =========================================================================
    @Nested
    @DisplayName("POST /api/accounts — создать счёт")
    class CreateAccountTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Валидный запрос → 201 Created + тело")
        void shouldReturn201() throws Exception {
            CreateAccountRequest req = new CreateAccountRequest();
            req.setCurrency(Currency.USD);

            when(bankAccountService.createAccount(eq("john_doe"), any())).thenReturn(sampleAccount(1L));

            mockMvc.perform(post("/api/accounts")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @WithMockUser
        @DisplayName("currency = null → 400 Bad Request (валидация)")
        void shouldReturn400WhenCurrencyNull() throws Exception {
            mockMvc.perform(post("/api/accounts")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\": null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Без аутентификации → 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/accounts")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\": \"USD\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // GET /api/accounts
    // =========================================================================
    @Nested
    @DisplayName("GET /api/accounts — список счетов")
    class GetMyAccountsTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Возвращает список счетов текущего пользователя → 200")
        void shouldReturn200WithAccountList() throws Exception {
            when(bankAccountService.getAccountsForUser("john_doe"))
                    .thenReturn(List.of(sampleAccount(1L), sampleAccount(2L)));

            mockMvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[1].id").value(2));
        }

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Нет счетов → 200 с пустым списком")
        void shouldReturn200WithEmptyList() throws Exception {
            when(bankAccountService.getAccountsForUser("john_doe")).thenReturn(List.of());

            mockMvc.perform(get("/api/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Без токена → 401")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/accounts"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // GET /api/accounts/{id}
    // =========================================================================
    @Nested
    @DisplayName("GET /api/accounts/{id} — счёт по id")
    class GetAccountByIdTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Существующий счёт → 200")
        void shouldReturn200ForExistingAccount() throws Exception {
            when(bankAccountService.getAccountById("john_doe", 42L)).thenReturn(sampleAccount(42L));

            mockMvc.perform(get("/api/accounts/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42));
        }

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Несуществующий счёт → 404")
        void shouldReturn404ForMissingAccount() throws Exception {
            when(bankAccountService.getAccountById("john_doe", 999L))
                    .thenThrow(new RuntimeException("Счёт не найден"));

            mockMvc.perform(get("/api/accounts/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // GET /api/accounts/{id}/balance
    // =========================================================================
    @Nested
    @DisplayName("GET /api/accounts/{id}/balance — баланс")
    class GetBalanceTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Возвращает баланс → 200")
        void shouldReturn200WithBalance() throws Exception {
            BalanceResponse balance = new BalanceResponse("ACC1", BigDecimal.valueOf(12345.67), Currency.USD);
            when(bankAccountService.getBalance("john_doe", 1L)).thenReturn(balance);

            mockMvc.perform(get("/api/accounts/1/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(12345.67))
                    .andExpect(jsonPath("$.currency").value("USD"));
        }
    }

    // =========================================================================
    // GET /api/accounts/lookup?accountNumber=...
    // =========================================================================
    @Nested
    @DisplayName("GET /api/accounts/lookup — поиск получателя")
    class LookupTests {

        @Test
        @WithMockUser
        @DisplayName("Существующий номер → 200 с данными получателя")
        void shouldReturn200WithRecipientInfo() throws Exception {
            RecipientInfoResponse info = new RecipientInfoResponse("ACC123", "Jane Doe", Currency.USD, AccountStatus.ACTIVE);
            when(bankAccountService.getRecipientInfo("ACC123")).thenReturn(info);

            mockMvc.perform(get("/api/accounts/lookup").param("accountNumber", "ACC123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountNumber").value("ACC123"))
                    .andExpect(jsonPath("$.ownerDisplayName").value("Jane Doe"));
        }
    }

    // =========================================================================
    // PATCH /api/accounts/{id}/block
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/accounts/{id}/block — блокировка")
    class BlockAccountTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Блокировка счёта → 200 + статус BLOCKED")
        void shouldReturn200WithBlockedStatus() throws Exception {
            BankAccountResponse blocked = new BankAccountResponse(
                    1L, "ACC1", BigDecimal.ZERO, Currency.USD, AccountStatus.BLOCKED, Instant.now());
            when(bankAccountService.blockAccount("john_doe", 1L)).thenReturn(blocked);

            mockMvc.perform(patch("/api/accounts/1/block").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("BLOCKED"));
        }
    }

    // =========================================================================
    // PATCH /api/accounts/{id}/unblock
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/accounts/{id}/unblock — разблокировка")
    class UnblockAccountTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Разблокировка счёта → 200 + статус ACTIVE")
        void shouldReturn200WithActiveStatus() throws Exception {
            when(bankAccountService.unblockAccount("john_doe", 1L)).thenReturn(sampleAccount(1L));

            mockMvc.perform(patch("/api/accounts/1/unblock").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    // =========================================================================
    // PATCH /api/accounts/{id}/close
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/accounts/{id}/close — закрытие")
    class CloseAccountTests {

        @Test
        @WithMockUser(username = "john_doe")
        @DisplayName("Закрытие счёта → 200 + статус CLOSED")
        void shouldReturn200WithClosedStatus() throws Exception {
            BankAccountResponse closed = new BankAccountResponse(
                    1L, "ACC1", BigDecimal.ZERO, Currency.USD, AccountStatus.CLOSED, Instant.now());
            when(bankAccountService.closeAccount("john_doe", 1L)).thenReturn(closed);

            mockMvc.perform(patch("/api/accounts/1/close").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }
    }
}