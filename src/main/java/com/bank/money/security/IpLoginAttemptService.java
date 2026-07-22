package com.bank.money.security;

import com.bank.money.exception.TooManyAttemptsException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IpLoginAttemptService {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 15 * 60;

    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public void checkAllowed(String ip) {
        Attempts attempts = attemptsByIp.get(ip);
        if (attempts != null
                && attempts.count >= MAX_ATTEMPTS
                && attempts.windowStart.plusSeconds(WINDOW_SECONDS).isAfter(Instant.now())) {
            throw new TooManyAttemptsException("Слишком много попыток входа с этого адреса. Попробуйте позже.");
        }
    }

    public void registerFailure(String ip) {
        attemptsByIp.compute(ip, (key, attempts) -> {
            Instant now = Instant.now();
            if (attempts == null || attempts.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Attempts(1, now);
            }
            attempts.count++;
            return attempts;
        });
    }

    public void registerSuccess(String ip) {
        attemptsByIp.remove(ip);
    }

    private static class Attempts {
        int count;
        final Instant windowStart;

        Attempts(int count, Instant windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }
}