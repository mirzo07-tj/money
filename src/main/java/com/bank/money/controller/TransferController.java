package com.bank.money.controller;

import com.bank.money.dto.CreateTransferRequest;
import com.bank.money.dto.P2PTransferRequest;
import com.bank.money.dto.TransferResponse;
import com.bank.money.entity.TransferType;
import com.bank.money.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Переводы между счетами")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/p2p")
    @Operation(summary = "Перевод другому пользователю по номеру счёта")
    public ResponseEntity<TransferResponse> transferToOtherUser(
            @Valid @RequestBody P2PTransferRequest request,
            Authentication authentication) {

        TransferResponse response = transferService.transferToOtherUser(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/own")
    @Operation(summary = "Перевод между своими счетами")
    public ResponseEntity<TransferResponse> transferBetweenOwnAccounts(
            @Valid @RequestBody CreateTransferRequest request,
            Authentication authentication) {

        TransferResponse response = transferService.transferBetweenOwnAccounts(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "История всех переводов текущего пользователя по всем его счетам, с фильтром по дате и типу")
    public ResponseEntity<List<TransferResponse>> getMyHistory(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) TransferType type) {

        List<TransferResponse> history = transferService.getMyHistory(authentication.getName(), from, to, type);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/accounts/{accountId}/history")
    @Operation(summary = "История переводов по конкретному счёту, с фильтром по дате и типу")
    public ResponseEntity<List<TransferResponse>> getAccountHistory(
            @PathVariable Long accountId,
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) TransferType type) {

        List<TransferResponse> history = transferService.getAccountHistory(authentication.getName(), accountId, from, to, type);
        return ResponseEntity.ok(history);
    }
}