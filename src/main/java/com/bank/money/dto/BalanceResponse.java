package com.bank.money.dto;

import com.bank.money.entity.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private Currency currency;
}
