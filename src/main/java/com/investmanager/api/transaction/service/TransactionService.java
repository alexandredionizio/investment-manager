package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.position.service.PositionService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import com.investmanager.api.transaction.mapper.TransactionMapper;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final TransactionMapper transactionMapper;
    private final PositionService positionService;

    public TransactionService(
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository,
            AssetRepository assetRepository,
            TransactionMapper transactionMapper,
            PositionService positionService) {

        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.transactionMapper = transactionMapper;
        this.positionService = positionService;
    }

    public TransactionResponse create(TransactionRequest request) {

        Portfolio portfolio = portfolioRepository
                .findById(request.portfolioId())
                        .orElseThrow(() ->
                                new PortfolioNotFoundException(request.portfolioId()));

        Asset asset = assetRepository
                .findById(request.assetId())
                .orElseThrow(() ->
                        new AssetNotFoundException(request.assetId()));

        if (request.type() == TransactionType.SELL) {

            PositionResponse currentPosition =
                    positionService.calculatePositionByAsset(
                            request.portfolioId(),
                            request.assetId()
                    );

            BigDecimal availableQuantity =
                    currentPosition == null
                            ? BigDecimal.ZERO
                            : currentPosition.quantity();

            if (request.quantity().compareTo(availableQuantity) > 0) {
                throw new InsufficientPositionException(asset.getTicker());
            }
        }

        Transaction transaction = new Transaction(
                portfolio,
                asset,
                request.type(),
                request.quantity(),
                request.unitPrice(),
                request.transactionDate()
        );

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        return transactionMapper.toResponse(savedTransaction);
    }

    public TransactionResponse findById(Long id) {

        Transaction transaction = transactionRepository
                .findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        return transactionMapper.toResponse(transaction);
    }

    public List<TransactionResponse> findAll() {

        return transactionRepository
                .findAll()
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    public List<TransactionResponse> findByPortfolioId(Long portfolioId) {

        return transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

}
