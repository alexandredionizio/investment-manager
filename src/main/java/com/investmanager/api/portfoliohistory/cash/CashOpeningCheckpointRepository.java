package com.investmanager.api.portfoliohistory.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CashOpeningCheckpointRepository extends JpaRepository<CashOpeningCheckpoint, Long> {
    Optional<CashOpeningCheckpoint> findByPortfolio_Id(Long portfolioId);
}
