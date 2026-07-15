package com.bank.money.service;

import com.bank.money.dto.CreateTransferRequest;
import com.bank.money.dto.P2PTransferRequest;
import com.bank.money.dto.TransferResponse;
import com.bank.money.entity.AccountStatus;
import com.bank.money.entity.BankAccount;
import com.bank.money.entity.Transfer;
import com.bank.money.entity.User;
import com.bank.money.repository.BankAccountRepository;
import com.bank.money.repository.TransferRepository;
import com.bank.money.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

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

        // Ключевая проверка для этого сценария: ОБА счёта должны принадлежать текущему пользователю
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
        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Недостаточно средств на счёте");
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

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

        // Списывать деньги может только владелец счёта-отправителя
        if (!from.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Вы не являетесь владельцем счёта-отправителя");
        }

        // На этот раз НЕ проверяем, что to принадлежит currentUser — это и есть p2p
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
        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Недостаточно средств на счёте");
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

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

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccount().getAccountNumber(),
                transfer.getToAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCreatedAt()
        );
    }
}