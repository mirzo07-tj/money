package com.bank.money.repository;

import com.bank.money.entity.PasswordResetToken;
import com.bank.money.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(
            String tokenHash, LocalDateTime now);

    void deleteByUser(User user);
}