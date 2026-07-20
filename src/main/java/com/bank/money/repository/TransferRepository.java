package com.bank.money.repository;

import com.bank.money.entity.Transfer;
import com.bank.money.entity.TransferType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @Query("""
            SELECT t FROM Transfer t
            WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
              AND (:from IS NULL OR t.createdAt >= :from)
              AND (:to IS NULL OR t.createdAt <= :to)
              AND (:type IS NULL OR t.type = :type)
            ORDER BY t.createdAt DESC
            """)
    List<Transfer> findAccountHistoryFiltered(
            @Param("accountId") Long accountId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("type") TransferType type);

    @Query("""
            SELECT t FROM Transfer t
            WHERE (t.fromAccount.id IN :accountIds OR t.toAccount.id IN :accountIds)
              AND (:from IS NULL OR t.createdAt >= :from)
              AND (:to IS NULL OR t.createdAt <= :to)
              AND (:type IS NULL OR t.type = :type)
            ORDER BY t.createdAt DESC
            """)
    List<Transfer> findMyHistoryFiltered(
            @Param("accountIds") List<Long> accountIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("type") TransferType type);
}