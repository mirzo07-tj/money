package com.bank.money.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(

        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        @Pattern(
                regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
                message = "Некорректный формат email"
        )
        @Size(max = 254, message = "Email слишком длинный")
        String newEmail,

        @NotBlank(message = "Текущий пароль обязателен для подтверждения")
        String currentPassword
) {}