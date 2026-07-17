package com.bank.money.controller;

import com.bank.money.dto.*;
import com.bank.money.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Операции со счетами")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @PostMapping
    @Operation(summary = "Создать новый счёт для текущего пользователя")
    public ResponseEntity<BankAccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {

        BankAccountResponse response = bankAccountService.createAccount(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Получить список счетов текущего пользователя")
    public ResponseEntity<List<BankAccountResponse>> getMyAccounts(Authentication authentication) {
        List<BankAccountResponse> accounts = bankAccountService.getAccountsForUser(authentication.getName());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить информацию о конкретном счёте")
    public ResponseEntity<BankAccountResponse> getAccount(
            @PathVariable Long id,
            Authentication authentication) {

        BankAccountResponse response = bankAccountService.getAccountById(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Заблокировать счёт")
    public ResponseEntity<BankAccountResponse> blockAccount(
            @PathVariable Long id,
            Authentication authentication) {

        BankAccountResponse response = bankAccountService.blockAccount(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Получить текущий баланс счёта")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long id,
            Authentication authentication) {

        BalanceResponse response = bankAccountService.getBalance(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Проверить существование получателя по номеру счёта перед переводом")
    public ResponseEntity<RecipientInfoResponse> lookupRecipient(
            @RequestParam String accountNumber) {

        RecipientInfoResponse response = bankAccountService.getRecipientInfo(accountNumber);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "Закрыть счёт")
    public ResponseEntity<BankAccountResponse> closeAccount(
            @PathVariable Long id,
            Authentication authentication) {

        BankAccountResponse response = bankAccountService.closeAccount(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Напрямую установить статус счёта (только администратор)")
    public ResponseEntity<BankAccountResponse> updateAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            Authentication authentication) {

        BankAccountResponse response = bankAccountService.updateAccountStatus(
                authentication.getName(), id, request.getStatus());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unblock")
    @Operation(summary = "Разблокировать счёт")
    public ResponseEntity<BankAccountResponse> unblockAccount(
            @PathVariable Long id,
            Authentication authentication) {

        BankAccountResponse response = bankAccountService.unblockAccount(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }
}