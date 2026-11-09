package com.investmanager.api.valuation;

import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ValuationContractService {
    private final ValuationContractRepository contracts;
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final JsonMapper json = JsonMapper.builder().build();
    public ValuationContractService(ValuationContractRepository contracts, PortfolioRepository portfolios, AssetRepository assets) {
        this.contracts=contracts; this.portfolios=portfolios; this.assets=assets;
    }
    public void authorize(Long portfolioId, Long userId) {
        portfolios.findByIdAndUserId(portfolioId,userId).orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }
    public ValuationTerms get(Long portfolioId, Long assetId, Long userId) {
        authorize(portfolioId,userId);
        return find(portfolioId,assetId);
    }
    public ValuationTerms find(Long portfolioId, Long assetId) {
        return contracts.findByPortfolioIdAndAssetId(portfolioId,assetId)
                .map(c -> json.readValue(c.getTermsJson(),ValuationTerms.class)).orElse(null);
    }
    @Transactional
    public ValuationTerms save(Long portfolioId, Long assetId, Long userId, ValuationTerms terms) {
        authorize(portfolioId,userId);
        terms.validate();
        var asset=assets.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        if (asset.getType() != (terms.treasury() ? AssetType.TREASURE : AssetType.FIXED_INCOME))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Contrato incompatível com a classe do ativo");
        String payload=json.writeValueAsString(terms);
        var contract=contracts.findByPortfolioIdAndAssetId(portfolioId,assetId)
                .orElseGet(() -> new ValuationContract(portfolioId,assetId,payload));
        contract.setTermsJson(payload);
        contracts.save(contract);
        return terms;
    }
}
