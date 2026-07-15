package com.bank.money.service;

import com.bank.money.dto.RegisterRequest;
import com.bank.money.dto.UserResponse;
import com.bank.money.entity.Role;
import com.bank.money.entity.User;
import com.bank.money.repository.RoleRepository;
import com.bank.money.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse register(RegisterRequest request) {
        log.info("Попытка регистрации пользователя username={}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Регистрация отклонена: username={} уже занят", request.getUsername());
            throw new IllegalArgumentException("Пользователь с таким username уже существует");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Регистрация отклонена: email={} уже используется", request.getEmail());
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Роль USER не найдена в базе"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);
        log.info("Пользователь зарегистрирован, id={}, роль=USER", saved.getId());

        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getCreatedAt(), Set.of("USER"));
    }

    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt(), roleNames);
    }
    public UserResponse updateUserRoles(Long userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: id=" + userId));

        Set<Role> newRoles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Роль не найдена: " + name)))
                .collect(Collectors.toSet());

        user.setRoles(newRoles);
        User saved = userRepository.save(user);

        log.info("Роли пользователя id={} обновлены на: {}", userId, roleNames);

        Set<String> updatedRoleNames = saved.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getCreatedAt(), updatedRoleNames);
    }
}