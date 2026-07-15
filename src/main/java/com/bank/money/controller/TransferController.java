package com.bank.money.controller;

import com.bank.money.dto.CreateTransferRequest;
import com.bank.money.dto.P2PTransferRequest;
import com.bank.money.dto.TransferResponse;
import com.bank.money.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}