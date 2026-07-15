package com.bank.money.dto;

import com.bank.money.entity.Currency;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccountRequest {

    @NotNull(message = "Валюта счёта обязательна")
    private Currency currency;
}