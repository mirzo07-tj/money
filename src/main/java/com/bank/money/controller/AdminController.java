package com.bank.money.controller;

import com.bank.money.dto.UpdateUserRolesRequest;
import com.bank.money.dto.UserResponse;
import com.bank.money.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Административные операции")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @PatchMapping("/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Изменить роли пользователя (только для ADMIN)")
    public ResponseEntity<UserResponse> updateUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        UserResponse response = userService.updateUserRoles(id, request.getRoles());
        return ResponseEntity.ok(response);
    }
}