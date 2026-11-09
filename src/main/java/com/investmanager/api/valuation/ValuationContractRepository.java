package com.investmanager.api.valuation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ValuationContractRepository extends JpaRepository<ValuationContract, Long> {
    Optional<ValuationContract> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);
}
