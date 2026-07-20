package com.bank.money.repository;

import com.bank.money.entity.BankAccount;
import com.bank.money.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    Optional<BankAccount> findByAccountNumber(String accountNumber);
    Optional<BankAccount> findByOwner_UsernameAndCurrency(String username, Currency currency);
    List<BankAccount> findByOwnerId(Long ownerId);

    List<BankAccount> findByOwner_Id(Long ownerId);
}
