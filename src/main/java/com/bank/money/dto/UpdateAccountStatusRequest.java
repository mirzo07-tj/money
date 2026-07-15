package com.bank.money.dto;

import com.bank.money.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAccountStatusRequest {

    @NotNull(message = "Статус обязателен")
    private AccountStatus status;
}