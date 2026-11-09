package com.investmanager.api.transaction.service;
import com.investmanager.api.asset.*;
import com.investmanager.api.asset.discovery.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.transaction.dto.*;
import com.investmanager.api.valuation.SupportedCryptocurrency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Public operation boundary. External identification precedes the existing bounded DB retry. */
@Service
public class TransactionOperationsService {
    private final BrapiCatalogClient catalog;
    private final InstrumentIdentityService identities;
    private final AssetRepository assets;
    private final PortfolioRepository portfolios;
    private final TransactionCreationExecutor executor;
    private final TransactionService transactions;
    public TransactionOperationsService(BrapiCatalogClient catalog, InstrumentIdentityService identities, AssetRepository assets,
            PortfolioRepository portfolios, TransactionCreationExecutor executor, TransactionService transactions) {
        this.catalog = catalog; this.identities = identities; this.assets = assets;
        this.portfolios = portfolios; this.executor = executor; this.transactions = transactions;
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse purchase(PurchaseRequest request, Long userId) {
        authorize(request.portfolioId(), userId);
        InstrumentEvidence evidence = catalog.confirm(request.ticker());
        if (!evidence.active()) throw InstrumentException.ineligible();
        return executor.execute(() -> {
            Asset asset = identities.registerConfirmed(evidence);
            return transactions.createConfirmed(new TransactionRequest(request.portfolioId(), asset.getId(), request.brokerId(),
                    TransactionType.BUY, request.quantity(), request.unitPrice(), request.transactionDate()), userId, evidence.ticker());
        });
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse create(TransactionRequest request, Long userId) {
        if (request.type() != TransactionType.BUY) return transactions.create(request, userId);
        authorize(request.portfolioId(), userId);
        Asset asset = assets.findById(request.assetId()).orElseThrow(() -> new AssetNotFoundException(request.assetId()));
        if (asset.getType() == AssetType.STOCK || asset.getType() == AssetType.FII || asset.getType() == AssetType.ETF) {
            String exchange = InstrumentEvidence.normalize(asset.getExchange());
            if (exchange != null && !exchange.isBlank() && !exchange.equals("B3")) throw InstrumentException.ineligible();
            InstrumentEvidence evidence = catalog.confirm(asset.getTicker());
            if (!evidence.active()) throw InstrumentException.ineligible();
            if (asset.getType() != evidence.type()) throw InstrumentException.ambiguous();
            return executor.execute(() -> {
                if (!identities.registerConfirmed(evidence).getId().equals(asset.getId())) throw InstrumentException.ambiguous();
                return transactions.createConfirmed(request, userId, evidence.ticker());
            });
        }
        // Existing non-B3 contracts retain their own identity/accounting flows; no ticker heuristics.
        if (asset.getType() == null || (asset.getType() == AssetType.CRYPTO && SupportedCryptocurrency.identify(asset.getTicker()).isEmpty()))
            throw InstrumentException.ineligible();
        return transactions.create(request, userId);
    }
    private void authorize(Long portfolioId, Long userId) {
        portfolios.findByIdAndUserId(portfolioId, userId).orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }
}
