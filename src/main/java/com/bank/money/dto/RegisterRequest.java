package com.bank.money.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Username обязателен")
    @Size(min = 3, max = 30, message = "Username должен быть от 3 до 30 символов")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username может содержать только буквы, цифры, точку и подчёркивание")
    private String username;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "Некорректный формат email"
    )
    @Size(max = 254, message = "Email слишком длинный")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 100, message = "Пароль должен быть не менее 8 символов")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "Пароль должен содержать хотя бы одну строчную, одну заглавную букву и цифру"
    )
    private String password;
}