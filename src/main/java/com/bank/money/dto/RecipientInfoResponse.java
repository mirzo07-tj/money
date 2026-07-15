package com.bank.money.dto;

import com.bank.money.entity.AccountStatus;
import com.bank.money.entity.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RecipientInfoResponse {
    private String accountNumber;
    private String ownerDisplayName;
    private Currency currency;
    private AccountStatus status;
}