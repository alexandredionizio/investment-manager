package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.service.IncomeEligibilityValidator;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.exception.BrokerNotFoundException;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import com.investmanager.api.transaction.mapper.TransactionMapper;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.transaction.validation.TransactionHistoryValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionHistoryValidator transactionHistoryValidator;
    private final BrokerRepository brokerRepository;
    private final PortfolioAssetLockManager lockManager;
    private final TransactionCreationExecutor creationExecutor;
    private final IncomeEligibilityValidator incomeEligibilityValidator;

    public TransactionService(
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository,
            AssetRepository assetRepository,
            BrokerRepository brokerRepository,
            TransactionMapper transactionMapper,
            TransactionHistoryValidator transactionHistoryValidator,
            PortfolioAssetLockManager lockManager,
            TransactionCreationExecutor creationExecutor,
            IncomeEligibilityValidator incomeEligibilityValidator) {

        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.brokerRepository = brokerRepository;
        this.transactionMapper = transactionMapper;
        this.transactionHistoryValidator = transactionHistoryValidator;
        this.lockManager = lockManager;
        this.creationExecutor = creationExecutor;
        this.incomeEligibilityValidator = incomeEligibilityValidator;
    }

    public TransactionResponse create(
            TransactionRequest request,
            Long userId) {

        return creationExecutor.execute(() -> createInTransaction(request, userId));
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public TransactionResponse createConfirmed(TransactionRequest request, Long userId, String ticker) {
        return createInTransaction(request, userId, ticker);
    }

    private TransactionResponse createInTransaction(TransactionRequest request, Long userId) {
        return createInTransaction(request, userId, null);
    }

    private TransactionResponse createInTransaction(TransactionRequest request, Long userId, String ticker) {

        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(
                        request.portfolioId(),
                        userId
                )
                .orElseThrow(() ->
                        new PortfolioNotFoundException(
                                request.portfolioId()
                        ));

        Asset asset = assetRepository
                .findById(request.assetId())
                .orElseThrow(() ->
                        new AssetNotFoundException(
                                request.assetId()
                        ));

        Broker broker = brokerRepository
                .findById(request.brokerId())
                .orElseThrow(() ->
                        new BrokerNotFoundException(
                                request.brokerId()
                        ));

        lockManager.acquire(request.portfolioId(), request.assetId());

        Transaction transaction = new Transaction(
                portfolio,
                asset,
                broker,
                request.type(),
                request.quantity(),
                request.unitPrice(),
                request.transactionDate()
        );

        transaction.setRecordedTicker(ticker == null ? asset.getTicker() : ticker);

        if (request.type() == TransactionType.SELL) {
            List<Transaction> history = transactionRepository
                    .findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(
                            request.portfolioId(),
                            request.assetId()
                    );
            transactionHistoryValidator.validateSale(history, transaction);
        }

        incomeEligibilityValidator.validate(request.portfolioId(), request.assetId(), transaction);

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        return transactionMapper.toResponse(savedTransaction);
    }

    public TransactionResponse findById(
            Long id,
            Long userId) {

        Transaction transaction = transactionRepository
                .findByIdAndPortfolioUserId(id, userId)
                .orElseThrow(() ->
                        new TransactionNotFoundException(id));

        return transactionMapper.toResponse(transaction);
    }

    public List<TransactionResponse> findAll(Long userId) {

        return transactionRepository
                .findAllByPortfolioUserId(userId)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    public List<TransactionResponse> findByPortfolioId(
            Long portfolioId,
            Long userId) {

        portfolioRepository
                .findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId));

        return transactionRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                        portfolioId,
                        userId
                )
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }
}
