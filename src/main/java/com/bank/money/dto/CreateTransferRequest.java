package com.bank.money.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateTransferRequest {

    @NotNull(message = "Счёт отправителя обязателен")
    private Long fromAccountId;

    @NotNull(message = "Счёт получателя обязателен")
    private Long toAccountId;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "0.01", message = "Сумма должна быть больше нуля")
    private BigDecimal amount;

    private String description;
}