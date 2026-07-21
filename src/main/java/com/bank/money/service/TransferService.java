package com.bank.money.service;

import com.bank.money.dto.CreateTransferRequest;
import com.bank.money.dto.Depositrequest;
import com.bank.money.dto.P2PTransferRequest;
import com.bank.money.dto.TransferResponse;
import com.bank.money.dto.WithdrawRequest;
import com.bank.money.entity.*;
import com.bank.money.repository.BankAccountRepository;
import com.bank.money.repository.TransferRepository;
import com.bank.money.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

    private static final Duration CANCEL_WINDOW = Duration.ofMinutes(1);
    private static final String SYSTEM_CASH_USERNAME = "SYSTEM_CASH";

    @Transactional
    public TransferResponse transferBetweenOwnAccounts(String username, CreateTransferRequest request) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Нельзя перевести на тот же счёт");
        }

        BankAccount from = bankAccountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Счёт отправителя не найден"));

        BankAccount to = bankAccountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Счёт получателя не найден"));

        if (!from.getOwner().getId().equals(currentUser.getId())
                || !to.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Перевод разрешён только между своими счетами");
        }

        if (from.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Счёт отправителя не активен");
        }
        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Счёт получателя не активен");
        }

        if (from.getCurrency() != to.getCurrency()) {
            throw new IllegalStateException(
                    "Перевод между разными валютами пока не поддерживается: "
                            + from.getCurrency() + " -> " + to.getCurrency());
        }

        BigDecimal amount = request.getAmount();

        from.debit(amount);
        to.credit(amount);

        bankAccountRepository.save(from);
        bankAccountRepository.save(to);

        Transfer transfer = new Transfer();
        transfer.setFromAccount(from);
        transfer.setToAccount(to);
        transfer.setAmount(amount);
        transfer.setCurrency(from.getCurrency());
        transfer.setDescription(request.getDescription());

        Transfer saved = transferRepository.save(transfer);

        log.info("Перевод между своими счетами id={}, {} -> {}, сумма={} {}, user={}",
                saved.getId(), from.getAccountNumber(), to.getAccountNumber(), amount, from.getCurrency(), username);

        return toResponse(saved);
    }

    @Transactional
    public TransferResponse transferToOtherUser(String username, P2PTransferRequest request) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount from = bankAccountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Счёт отправителя не найден"));

        BankAccount to = bankAccountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Счёт с таким номером не найден"));

        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Нельзя перевести на тот же счёт");
        }

        if (!from.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Вы не являетесь владельцем счёта-отправителя");
        }

        if (to.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Для перевода себе используйте /api/transfers/own");
        }

        if (from.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Счёт отправителя не активен");
        }
        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Счёт получателя не активен");
        }

        if (from.getCurrency() != to.getCurrency()) {
            throw new IllegalStateException(
                    "Перевод между разными валютами пока не поддерживается: "
                            + from.getCurrency() + " -> " + to.getCurrency());
        }

        BigDecimal amount = request.getAmount();

        from.debit(amount);
        to.credit(amount);

        bankAccountRepository.save(from);
        bankAccountRepository.save(to);

        Transfer transfer = new Transfer();
        transfer.setFromAccount(from);
        transfer.setToAccount(to);
        transfer.setAmount(amount);
        transfer.setCurrency(from.getCurrency());
        transfer.setDescription(request.getDescription());

        Transfer saved = transferRepository.save(transfer);

        log.info("P2P-перевод id={}, {} -> {}, сумма={} {}, отправитель={}",
                saved.getId(), from.getAccountNumber(), to.getAccountNumber(), amount, from.getCurrency(), username);

        return toResponse(saved);
    }

    @Transactional
    public TransferResponse cancelTransfer(String username, Long transferId) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        Transfer original = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Перевод не найден"));

        if (!original.getFromAccount().getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Вы не можете отменить этот перевод");
        }

        if (original.isCancelled()) {
            throw new IllegalStateException("Перевод уже отменён");
        }

        if (original.getReversalOf() != null) {
            throw new IllegalStateException("Нельзя отменить перевод, который сам является отменой другого перевода");
        }

        Duration elapsed = Duration.between(original.getCreatedAt(), Instant.now());
        if (elapsed.compareTo(CANCEL_WINDOW) > 0) {
            throw new IllegalStateException(
                    "Время для отмены перевода истекло (доступно только в течение "
                            + CANCEL_WINDOW.toMinutes() + " минуты после перевода)");
        }

        BankAccount originalFrom = original.getFromAccount();
        BankAccount originalTo = original.getToAccount();
        BigDecimal amount = original.getAmount();

        originalTo.debit(amount);
        originalFrom.credit(amount);

        bankAccountRepository.save(originalTo);
        bankAccountRepository.save(originalFrom);

        original.setCancelled(true);
        original.setCancelledAt(Instant.now());
        transferRepository.save(original);

        Transfer reversal = new Transfer();
        reversal.setFromAccount(originalTo);
        reversal.setToAccount(originalFrom);
        reversal.setAmount(amount);
        reversal.setCurrency(original.getCurrency());
        reversal.setDescription("Отмена перевода #" + original.getId());
        reversal.setReversalOf(original);

        Transfer savedReversal = transferRepository.save(reversal);

        log.info("Перевод id={} отменён пользователем={}, создан реверс id={}",
                original.getId(), username, savedReversal.getId());

        return toResponse(savedReversal);
    }

    @Transactional
    public TransferResponse depositToAccount(String username, Depositrequest request) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount target = bankAccountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден"));

        if (!target.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Пополнить можно только свой счёт");
        }

        if (target.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Счёт не активен");
        }

        BankAccount cashAccount = bankAccountRepository
                .findByOwner_UsernameAndCurrency(SYSTEM_CASH_USERNAME, target.getCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "Касса для валюты " + target.getCurrency() + " не настроена"));

        BigDecimal amount = request.getAmount();

        cashAccount.setBalance(cashAccount.getBalance().subtract(amount));
        target.credit(amount);

        bankAccountRepository.save(cashAccount);
        bankAccountRepository.save(target);

        Transfer transfer = new Transfer();
        transfer.setFromAccount(cashAccount);
        transfer.setToAccount(target);
        transfer.setAmount(amount);
        transfer.setCurrency(target.getCurrency());
        transfer.setDescription(request.getDescription() != null ? request.getDescription() : "Пополнение счёта");

        Transfer saved = transferRepository.save(transfer);

        log.info("Пополнение счёта id={} на сумму {} {}, user={}",
                target.getId(), amount, target.getCurrency(), username);

        return toResponse(saved);
    }

    @Transactional
    public TransferResponse withdrawFromAccount(String username, WithdrawRequest request) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount source = bankAccountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден"));

        if (!source.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Списать можно только со своего счёта");
        }

        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Счёт не активен");
        }

        BankAccount cashAccount = bankAccountRepository
                .findByOwner_UsernameAndCurrency(SYSTEM_CASH_USERNAME, source.getCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "Касса для валюты " + source.getCurrency() + " не настроена"));

        BigDecimal amount = request.getAmount();

        source.debit(amount);
        cashAccount.credit(amount);

        bankAccountRepository.save(source);
        bankAccountRepository.save(cashAccount);

        Transfer transfer = new Transfer();
        transfer.setFromAccount(source);
        transfer.setToAccount(cashAccount);
        transfer.setAmount(amount);
        transfer.setCurrency(source.getCurrency());
        transfer.setDescription(request.getDescription() != null ? request.getDescription() : "Списание со счёта");

        Transfer saved = transferRepository.save(transfer);

        log.info("Списание со счёта id={} на сумму {} {}, user={}",
                source.getId(), amount, source.getCurrency(), username);

        return toResponse(saved);
    }

    // ===== История: единственные версии, с фильтром + пагинацией + сортировкой =====

    @Transactional(readOnly = true)
    public Page<TransferResponse> getMyHistory(String username, Instant from, Instant to,
                                               TransferType type, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        validateDateRange(from, to);

        List<Long> accountIds = bankAccountRepository.findByOwner_Id(currentUser.getId())
                .stream()
                .map(BankAccount::getId)
                .collect(Collectors.toList());

        if (accountIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return transferRepository.findMyHistoryFiltered(accountIds, from, to, type, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> getAccountHistory(String username, Long accountId, Instant from, Instant to,
                                                    TransferType type, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден"));

        if (!account.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Это не ваш счёт");
        }

        validateDateRange(from, to);

        return transferRepository.findAccountHistoryFiltered(accountId, from, to, type, pageable)
                .map(this::toResponse);
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccount().getAccountNumber(),
                transfer.getToAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCreatedAt(),
                transfer.isCancelled(),
                transfer.getCancelledAt(),
                transfer.getReversalOf() != null ? transfer.getReversalOf().getId() : null
        );
    }
}