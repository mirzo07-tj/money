package com.bank.money.security;

import com.bank.money.entity.Role;
import com.bank.money.entity.User;
import com.bank.money.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для {@link CustomUserDetailsService}.
 *
 * Проверяем маппинг User → Spring Security UserDetails:
 * - username/password проставляются
 * - роли маппятся в ROLE_XXX authorities
 * - несуществующий пользователь → UsernameNotFoundException
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    // ===================== Успешная загрузка =====================

    @Nested
    @DisplayName("loadUserByUsername() — успешный сценарий")
    class SuccessTests {

        @Test
        @DisplayName("Должен вернуть UserDetails с правильным username")
        void shouldReturnCorrectUsername() {
            User user = createUser("john", "hashed_pw", "USER");
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

            UserDetails details = service.loadUserByUsername("john");

            assertEquals("john", details.getUsername());
        }

        @Test
        @DisplayName("Должен вернуть UserDetails с правильным password hash")
        void shouldReturnCorrectPassword() {
            User user = createUser("john", "$2a$10$hash...", "USER");
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

            UserDetails details = service.loadUserByUsername("john");

            assertEquals("$2a$10$hash...", details.getPassword());
        }

        @Test
        @DisplayName("Одна роль USER → authority ROLE_USER")
        void shouldMapSingleRoleToAuthority() {
            User user = createUser("john", "pw", "USER");
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

            UserDetails details = service.loadUserByUsername("john");

            Set<String> authorities = details.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            assertEquals(1, authorities.size());
            assertTrue(authorities.contains("ROLE_USER"));
        }

        @Test
        @DisplayName("Две роли USER и ADMIN → два authority")
        void shouldMapMultipleRolesToAuthorities() {
            User user = createUser("admin", "pw", "USER", "ADMIN");
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

            UserDetails details = service.loadUserByUsername("admin");

            Set<String> authorities = details.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            assertEquals(2, authorities.size());
            assertTrue(authorities.contains("ROLE_USER"));
            assertTrue(authorities.contains("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("Должен вызвать userRepository.findByUsername() ровно 1 раз")
        void shouldCallRepositoryOnce() {
            User user = createUser("john", "pw", "USER");
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

            service.loadUserByUsername("john");

            verify(userRepository, times(1)).findByUsername("john");
        }
    }

    // ===================== Пользователь не найден =====================

    @Nested
    @DisplayName("loadUserByUsername() — пользователь не найден")
    class NotFoundTests {

        @Test
        @DisplayName("Несуществующий username → UsernameNotFoundException")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UsernameNotFoundException ex = assertThrows(
                    UsernameNotFoundException.class,
                    () -> service.loadUserByUsername("ghost")
            );
            assertTrue(ex.getMessage().contains("ghost"));
        }

        @Test
        @DisplayName("Сообщение исключения содержит имя пользователя")
        void shouldIncludeUsernameInExceptionMessage() {
            when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

            UsernameNotFoundException ex = assertThrows(
                    UsernameNotFoundException.class,
                    () -> service.loadUserByUsername("unknown_user")
            );
            assertTrue(ex.getMessage().contains("unknown_user"));
        }
    }

    // ===================== Граничные случаи =====================

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Пользователь без ролей → пустой набор authorities")
        void shouldHandleUserWithNoRoles() {
            User user = createUser("noroles", "pw"); // без ролей
            when(userRepository.findByUsername("noroles")).thenReturn(Optional.of(user));

            UserDetails details = service.loadUserByUsername("noroles");

            assertTrue(details.getAuthorities().isEmpty());
        }
    }

    // ===================== Фабрика тестовых объектов =====================

    private User createUser(String username, String passwordHash, String... roleNames) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);

        for (String roleName : roleNames) {
            Role role = new Role();
            role.setName(roleName);
            user.getRoles().add(role);
        }

        return user;
    }
}
