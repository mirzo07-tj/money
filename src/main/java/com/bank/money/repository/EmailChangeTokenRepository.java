package com.bank.money.repository;

import com.bank.money.entity.EmailChangeToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, Long> {

    Optional<EmailChangeToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(
            String tokenHash, LocalDateTime now);
}