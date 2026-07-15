package com.bank.money.dto;

import com.bank.money.entity.AccountStatus;
import com.bank.money.entity.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class BankAccountResponse {
    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private Currency currency;
    private AccountStatus status;
    private Instant createdAt;
}