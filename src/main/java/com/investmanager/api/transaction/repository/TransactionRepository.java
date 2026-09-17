package com.investmanager.api.transaction.repository;

import com.investmanager.api.transaction.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = {
            "portfolio",
            "asset",
            "broker"
    })
    List<Transaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(
            Long portfolioId
    );

    @EntityGraph(attributePaths = {
            "portfolio",
            "asset",
            "broker"
    })
    List<Transaction> findByPortfolioIdAndAssetIdAndTransactionDateLessThanEqualOrderByTransactionDateAscIdAsc(
            Long portfolioId,
            Long assetId,
            LocalDate transactionDate
    );

    @EntityGraph(attributePaths = {
            "portfolio",
            "asset",
            "broker"
    })
    Optional<Transaction> findByIdAndPortfolioUserId(
            Long id,
            Long userId
    );

    @EntityGraph(attributePaths = {
            "portfolio",
            "asset",
            "broker"
    })
    List<Transaction> findAllByPortfolioUserId(
            Long userId
    );

    @EntityGraph(attributePaths = {
            "portfolio",
            "asset",
            "broker"
    })
    List<Transaction> findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
            Long portfolioId,
            Long userId
    );
}