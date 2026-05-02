package com.hdfc.banking.repository;


import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hdfc.banking.entity.Account;
import com.hdfc.banking.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

   Page<Transaction> findByFromAccountOrToAccountOrderByCreatedAtDesc(
    Account fromAccount,
    Account toAccount,
    Pageable pageable
);

  Optional<Transaction>findByReferenceNumber(String referenceNumber);
}
