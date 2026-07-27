package com.bank.money.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Токен обязателен")
        String token,

        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 8, max = 100, message = "Пароль должен быть не менее 8 символов")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Пароль должен содержать хотя бы одну строчную, одну заглавную букву и цифру"
        )
        String newPassword
) {}