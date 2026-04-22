package com.hdfc.banking.repository;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByfromAccountOrBytoAccountOrderByCreatedAt(Account fromAccount, Account toAccount,
            Pageable pageable);
}
