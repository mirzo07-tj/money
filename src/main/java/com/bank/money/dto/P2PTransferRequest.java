package com.bank.money.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class P2PTransferRequest {

    @NotNull(message = "Счёт отправителя обязателен")
    private Long fromAccountId;

    @NotBlank(message = "Номер счёта получателя обязателен")
    private String toAccountNumber;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "0.01", message = "Сумма должна быть больше нуля")
    @DecimalMax(value = "1000000.00", message = "Максимальная сумма")
    private BigDecimal amount;

    private String description;
}