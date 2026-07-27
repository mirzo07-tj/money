package com.bank.money.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
    @Digits(integer = 17, fraction = 2, message = "Сумма должна иметь не более 2 знаков после запятой")
    private BigDecimal amount;

    private String description;
}