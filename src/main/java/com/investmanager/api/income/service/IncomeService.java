package com.investmanager.api.income.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.income.exception.InvalidIncomeDatesException;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.income.exception.NoPositionOnBaseDateException;
import com.investmanager.api.income.mapper.IncomeMapper;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.service.PositionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

@Service
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final IncomeMapper incomeMapper;
    private final PositionService positionService;
    private final PortfolioAssetLockManager lockManager;
    private final TransactionCreationExecutor creationExecutor;
    private final IncomePaymentPolicy paymentPolicy;

    public IncomeService(
            IncomeRepository incomeRepository,
            PortfolioRepository portfolioRepository,
            AssetRepository assetRepository,
            IncomeMapper incomeMapper,
            PositionService positionService,
            PortfolioAssetLockManager lockManager,
            TransactionCreationExecutor creationExecutor,
            IncomePaymentPolicy paymentPolicy) {

        this.incomeRepository = incomeRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.incomeMapper = incomeMapper;
        this.positionService = positionService;
        this.lockManager = lockManager;
        this.creationExecutor = creationExecutor;
        this.paymentPolicy = paymentPolicy;
    }

    public IncomeResponse create(
            IncomeRequest request,
            Long userId) {

        return creationExecutor.execute(() -> createInTransaction(request, userId));
    }

    private IncomeResponse createInTransaction(IncomeRequest request, Long userId) {
        if (request.paymentDate().isBefore(request.baseDate())) {
            throw new InvalidIncomeDatesException();
        }

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

        lockManager.acquire(request.portfolioId(), request.assetId());

        PositionResponse position =
                positionService.calculatePositionByAssetAndDate(
                        request.portfolioId(),
                        request.assetId(),
                        request.baseDate()
                );

        if (position == null
                || position.quantity()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new NoPositionOnBaseDateException(
                    asset.getTicker(),
                    request.baseDate()
            );
        }

        BigDecimal eligibleQuantity =
                position.quantity();

        Income income = new Income(
                portfolio,
                asset,
                request.type(),
                request.amountPerUnit(),
                eligibleQuantity,
                request.baseDate(),
                request.paymentDate()
        );

        Income savedIncome =
                incomeRepository.save(income);

        return incomeMapper.toResponse(savedIncome, paymentPolicy.today());
    }

    @Transactional(readOnly = true)
    public IncomeResponse findById(
            Long id,
            Long userId) {

        Income income = incomeRepository
                .findByIdAndPortfolioUserId(id, userId)
                .orElseThrow(() ->
                        new IncomeNotFoundException(id));

        return incomeMapper.toResponse(income, paymentPolicy.today());
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> findAll(Long userId) {
        LocalDate today = paymentPolicy.today();

        return incomeRepository
                .findAllByPortfolioUserId(userId)
                .stream()
                .map(income -> incomeMapper.toResponse(income, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> findByPortfolioId(
            Long portfolioId,
            Long userId) {

        portfolioRepository
                .findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId));

        LocalDate today = paymentPolicy.today();
        return incomeRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        portfolioId,
                        userId
                )
                .stream()
                .map(income -> incomeMapper.toResponse(income, today))
                .toList();
    }
}