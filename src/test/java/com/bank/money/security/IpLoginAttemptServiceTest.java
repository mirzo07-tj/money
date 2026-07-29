package com.bank.money.security;

import com.bank.money.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для {@link IpLoginAttemptService}.
 *
 * Тестируем brute-force защиту по IP:
 * - Счётчик попыток
 * - Блокировка после MAX_ATTEMPTS (10)
 * - Сброс при успешном логине
 * - Окно сброса (15 минут)
 */
class IpLoginAttemptServiceTest {

    private IpLoginAttemptService service;

    private static final String TEST_IP = "192.168.1.100";
    private static final String OTHER_IP = "10.0.0.1";

    @BeforeEach
    void setUp() {
        service = new IpLoginAttemptService();
    }

    // ===================== checkAllowed =====================

    @Nested
    @DisplayName("checkAllowed()")
    class CheckAllowedTests {

        @Test
        @DisplayName("Новый IP → доступ разрешён (нет записей)")
        void shouldAllowNewIp() {
            assertDoesNotThrow(() -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("9 неудачных попыток → ещё разрешено")
        void shouldAllowUpToMaxMinusOneAttempts() {
            for (int i = 0; i < 9; i++) {
                service.registerFailure(TEST_IP);
            }

            assertDoesNotThrow(() -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("10 неудачных попыток → блокировка (TooManyAttemptsException)")
        void shouldBlockAfterMaxAttempts() {
            for (int i = 0; i < 10; i++) {
                service.registerFailure(TEST_IP);
            }

            TooManyAttemptsException ex = assertThrows(
                    TooManyAttemptsException.class,
                    () -> service.checkAllowed(TEST_IP)
            );
            assertTrue(ex.getMessage().contains("Слишком много попыток"));
        }

        @Test
        @DisplayName("11 попыток → всё ещё заблокирован")
        void shouldStayBlockedAfterMoreAttempts() {
            for (int i = 0; i < 11; i++) {
                service.registerFailure(TEST_IP);
            }

            assertThrows(TooManyAttemptsException.class,
                    () -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("Блокировка одного IP не затрагивает другой")
        void shouldNotAffectDifferentIps() {
            for (int i = 0; i < 10; i++) {
                service.registerFailure(TEST_IP);
            }

            // TEST_IP заблокирован
            assertThrows(TooManyAttemptsException.class,
                    () -> service.checkAllowed(TEST_IP));

            // OTHER_IP — свободен
            assertDoesNotThrow(() -> service.checkAllowed(OTHER_IP));
        }
    }

    // ===================== registerSuccess =====================

    @Nested
    @DisplayName("registerSuccess()")
    class RegisterSuccessTests {

        @Test
        @DisplayName("Успешный логин сбрасывает счётчик попыток")
        void shouldResetAttemptsAfterSuccess() {
            // Набираем 10 неудачных → блокировка
            for (int i = 0; i < 10; i++) {
                service.registerFailure(TEST_IP);
            }
            assertThrows(TooManyAttemptsException.class,
                    () -> service.checkAllowed(TEST_IP));

            // Успешный логин
            service.registerSuccess(TEST_IP);

            // Теперь снова разрешено
            assertDoesNotThrow(() -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("registerSuccess для несуществующего IP — без ошибок")
        void shouldNotThrowForUnknownIp() {
            assertDoesNotThrow(() -> service.registerSuccess("255.255.255.255"));
        }
    }

    // ===================== registerFailure =====================

    @Nested
    @DisplayName("registerFailure()")
    class RegisterFailureTests {

        @Test
        @DisplayName("Первая ошибка создаёт запись (не блокирует)")
        void shouldCreateRecordOnFirstFailure() {
            service.registerFailure(TEST_IP);

            assertDoesNotThrow(() -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("Ошибки для разных IP учитываются раздельно")
        void shouldTrackIpsSeparately() {
            for (int i = 0; i < 5; i++) {
                service.registerFailure(TEST_IP);
            }
            for (int i = 0; i < 3; i++) {
                service.registerFailure(OTHER_IP);
            }

            // Ни один не заблокирован
            assertDoesNotThrow(() -> service.checkAllowed(TEST_IP));
            assertDoesNotThrow(() -> service.checkAllowed(OTHER_IP));
        }
    }

    // ===================== Граничные случаи =====================

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Ровно 10-я попытка — порог блокировки")
        void shouldBlockExactlyAtThreshold() {
            for (int i = 0; i < 10; i++) {
                service.registerFailure(TEST_IP);
            }

            assertThrows(TooManyAttemptsException.class,
                    () -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("Сброс после успеха, затем новая серия ошибок — отсчёт заново")
        void shouldResetCounterAfterSuccessThenFailAgain() {
            // Набиваем 10 ошибок
            for (int i = 0; i < 10; i++) {
                service.registerFailure(TEST_IP);
            }
            // Сбрасываем
            service.registerSuccess(TEST_IP);

            // Набиваем ещё 9 — не блокирует
            for (int i = 0; i < 9; i++) {
                service.registerFailure(TEST_IP);
            }
            assertDoesNotThrow(() -> service.checkAllowed(TEST_IP));

            // 10-я → блокировка
            service.registerFailure(TEST_IP);
            assertThrows(TooManyAttemptsException.class,
                    () -> service.checkAllowed(TEST_IP));
        }

        @Test
        @DisplayName("IPv6-адрес обрабатывается как обычный ключ")
        void shouldHandleIpv6Address() {
            String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

            service.registerFailure(ipv6);
            assertDoesNotThrow(() -> service.checkAllowed(ipv6));

            for (int i = 1; i < 10; i++) {
                service.registerFailure(ipv6);
            }
            assertThrows(TooManyAttemptsException.class,
                    () -> service.checkAllowed(ipv6));
        }
    }
}
