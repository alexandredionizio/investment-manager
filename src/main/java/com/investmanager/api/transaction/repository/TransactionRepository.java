package com.investmanager.api.transaction.repository;

import com.investmanager.api.transaction.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = "asset")
    List<Transaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(Long portfolioId);
}
