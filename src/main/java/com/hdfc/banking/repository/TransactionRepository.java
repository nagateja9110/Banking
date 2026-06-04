package com.hdfc.banking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByFromAccountOrToAccountOrderByCreatedAtDesc(
            Account fromAccount,
            Account toAccount,
            Pageable pageable);

    @Query("""
                SELECT t FROM Transaction t
                Where (t.fromAccount=:account OR t.toAccount=:account)
                and t.createdAt between :from and :to
                Order by t.createdAt DESC
            """)
    List<Transaction> findByAccountAndDateRange(
            @Param("account") Account account,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
                SELECT COUNT(t) FROM Transaction t
                WHERE t.fromAccount = :account
                AND t.transactionType = 'TRANSFER'
                AND t.createdAt >= :since
            """)
    long countRecentTransfers(
            @Param("account") Account account,
            @Param("since") LocalDateTime since);

    Optional<Transaction> findByReferenceNumber(String referenceNumber);
}
