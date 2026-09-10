package com.investmanager.api.income.repository;

import com.investmanager.api.income.Income;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByPortfolioIdOrderByPaymentDateAscIdAsc(
            Long portfolioId
    );

    Optional<Income> findByIdAndPortfolioUserId(
            Long id,
            Long userId
    );

    List<Income> findAllByPortfolioUserId(
            Long userId
    );

    List<Income> findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
            Long portfolioId,
            Long userId
    );
}