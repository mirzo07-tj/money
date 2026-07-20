package com.bank.money.dto;

import com.bank.money.entity.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class TransferResponse {
    private Long id;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private Currency currency;
    private String description;
    private Instant createdAt;
    private boolean cancelled;
    private Instant cancelledAt;
    private Long reversalOfId; // id перевода, который этот перевод отменяет (null, если это не реверс)
}
