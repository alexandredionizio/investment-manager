package com.investmanager.api.portfolio.repository;

import com.investmanager.api.portfolio.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findAllByUserId(Long userId);

    Optional<Portfolio> findByIdAndUserId(Long id, Long userId);
}