package com.bank.money.service;

import com.bank.money.dto.BalanceResponse;
import com.bank.money.dto.BankAccountResponse;
import com.bank.money.dto.CreateAccountRequest;
import com.bank.money.dto.RecipientInfoResponse;
import com.bank.money.entity.AccountStatus;
import com.bank.money.entity.BankAccount;
import com.bank.money.entity.User;
import com.bank.money.repository.BankAccountRepository;
import com.bank.money.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

    public BankAccountResponse createAccount(String username, CreateAccountRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = new BankAccount();
        account.setOwner(user);
        account.setCurrency(request.getCurrency());
        account.setStatus(AccountStatus.ACTIVE);

        BankAccount saved = bankAccountRepository.save(account);
        log.info("Создан счёт id={}, accountNumber={}, owner={}, currency={}",
                saved.getId(), saved.getAccountNumber(), username, saved.getCurrency());

        return toResponse(saved);
    }

    public RecipientInfoResponse getRecipientInfo(String accountNumber) {
        BankAccount account = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Счёт с таким номером не найден"));

        return new RecipientInfoResponse(
                account.getAccountNumber(),
                maskUsername(account.getOwner().getUsername()),
                account.getCurrency(),
                account.getStatus()
        );
    }

    private String maskUsername(String username) {
        if (username.length() <= 2) {
            return username.charAt(0) + "***";
        }
        return username.substring(0, 2) + "***";
    }

    public List<BankAccountResponse> getAccountsForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        List<BankAccount> accounts = bankAccountRepository.findByOwnerId(user.getId());

        return accounts.stream()
                .map(this::toResponse)
                .toList();
    }

    public BankAccountResponse getAccountById(String username, Long accountId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: id=" + accountId));

        if (!account.getOwner().getId().equals(user.getId())) {
            log.warn("Попытка доступа к чужому счёту: username={}, accountId={}", username, accountId);
            throw new AccessDeniedException("Нет доступа к этому счёту");
        }

        return toResponse(account);
    }

    @Transactional
    public BankAccountResponse blockAccount(String username, Long accountId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: id=" + accountId));

        if (!isOwnerOrAdmin(user, account)) {
            log.warn("Попытка заблокировать чужой счёт: username={}, accountId={}", username, accountId);
            throw new AccessDeniedException("Нет доступа к этому счёту");
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Счёт закрыт, блокировка невозможна");
        }
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new IllegalStateException("Счёт уже заблокирован");
        }

        account.setStatus(AccountStatus.BLOCKED);
        BankAccount saved = bankAccountRepository.save(account);
        log.info("Счёт заблокирован: id={}, инициатор={}", accountId, username);

        return toResponse(saved);
    }

    @Transactional
    public BankAccountResponse closeAccount(String username, Long accountId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: id=" + accountId));

        if (!isOwnerOrAdmin(user, account)) {
            log.warn("Попытка закрыть чужой счёт: username={}, accountId={}", username, accountId);
            throw new AccessDeniedException("Нет доступа к этому счёту");
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Счёт уже закрыт");
        }

        account.setStatus(AccountStatus.CLOSED);
        BankAccount saved = bankAccountRepository.save(account);
        log.info("Счёт закрыт: id={}, инициатор={}, balance={}", accountId, username, saved.getBalance());

        return toResponse(saved);
    }

    @Transactional
    public BankAccountResponse updateAccountStatus(String username, Long accountId, AccountStatus newStatus) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        if (!isAdmin(user)) {
            log.warn("Попытка напрямую изменить статус счёта без прав администратора: username={}, accountId={}",
                    username, accountId);
            throw new AccessDeniedException("Только администратор может напрямую менять статус счёта");
        }

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: id=" + accountId));

        if (account.getStatus() == newStatus) {
            throw new IllegalStateException("Счёт уже имеет статус " + newStatus);
        }

        AccountStatus oldStatus = account.getStatus();
        account.setStatus(newStatus);
        BankAccount saved = bankAccountRepository.save(account);

        log.info("Администратор {} изменил статус счёта id={}: {} -> {}",
                username, accountId, oldStatus, newStatus);

        return toResponse(saved);
    }

    public BalanceResponse getBalance(String username, Long accountId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: id=" + accountId));

        if (!account.getOwner().getId().equals(user.getId())) {
            log.warn("Попытка узнать баланс чужого счёта: username={}, accountId={}", username, accountId);
            throw new AccessDeniedException("Нет доступа к этому счёту");
        }

        return new BalanceResponse(account.getAccountNumber(), account.getBalance(), account.getCurrency());
    }

    @Transactional
    public BankAccountResponse unblockAccount(String username, Long accountId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: id=" + accountId));

        if (!isOwnerOrAdmin(user, account)) {
            log.warn("Попытка разблокировать чужой счёт: username={}, accountId={}", username, accountId);
            throw new AccessDeniedException("Нет доступа к этому счёту");
        }


        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Счёт закрыт, операция невозможна");
        }
        if (account.getStatus() != AccountStatus.BLOCKED) {
            throw new IllegalStateException("Счёт не заблокирован, разблокировка невозможна");
        }

        account.setStatus(AccountStatus.ACTIVE);
        BankAccount saved = bankAccountRepository.save(account);
        log.info("Счёт разблокирован: id={}, инициатор={}", accountId, username);

        return toResponse(saved);
    }

    private boolean isOwnerOrAdmin(User user, BankAccount account) {
        boolean isOwner = account.getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase("ADMIN"));
        return isOwner || isAdmin;
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase("ADMIN"));
    }

    private BankAccountResponse toResponse(BankAccount account) {
        return new BankAccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}

