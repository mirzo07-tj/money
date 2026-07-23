package com.bank.money.controller;

import com.bank.money.dto.ChangeEmailRequest;
import com.bank.money.dto.ConfirmEmailChangeRequest;
import com.bank.money.service.EmailChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Операции с пользователями")
@RequiredArgsConstructor
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    @PostMapping("/me/change-email")
    @Operation(summary = "Запросить смену email (отправляет письмо-подтверждение на новый адрес)")
    public ResponseEntity<Map<String, String>> changeEmail(
            Authentication authentication,
            @Valid @RequestBody ChangeEmailRequest request) {

        emailChangeService.requestEmailChange(
                authentication.getName(), request.newEmail(), request.currentPassword());

        return ResponseEntity.ok(Map.of(
                "message", "Письмо с подтверждением отправлено на новый адрес почты"
        ));
    }

    @PostMapping("/confirm-email-change")
    @Operation(summary = "Подтвердить смену email по токену из письма")
    public ResponseEntity<Map<String, String>> confirmEmailChange(
            @Valid @RequestBody ConfirmEmailChangeRequest request) {

        emailChangeService.confirmEmailChange(request.token());

        return ResponseEntity.ok(Map.of("message", "Email успешно изменён"));
    }
}