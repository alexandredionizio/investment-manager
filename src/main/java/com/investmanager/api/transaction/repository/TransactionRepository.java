package com.investmanager.api.transaction.repository;

import com.investmanager.api.transaction.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = "asset")
    List<Transaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(
            Long portfolioId
    );

    Optional<Transaction> findByIdAndPortfolioUserId(
            Long id,
            Long userId
    );

    @EntityGraph(attributePaths = "asset")
    List<Transaction> findAllByPortfolioUserId(
            Long userId
    );

    @EntityGraph(attributePaths = "asset")
    List<Transaction> findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
            Long portfolioId,
            Long userId
    );
}