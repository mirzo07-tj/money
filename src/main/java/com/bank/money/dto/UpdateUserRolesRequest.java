package com.bank.money.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateUserRolesRequest {

    @NotEmpty(message = "Список ролей не может быть пустым")
    private Set<String> roles;
}