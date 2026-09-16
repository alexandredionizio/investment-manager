package com.investmanager.api.position.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.dto.PositionMarketResponse;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.quote.service.QuoteService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PositionService {

    private static final int CALCULATION_SCALE = 12;
    private static final int DISPLAY_SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final QuoteService quoteService;

    public PositionService(
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository,
            QuoteService quoteService) {

        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.quoteService = quoteService;
    }

    public List<PositionResponse> calculatePositions(
            Long portfolioId,
            Long userId) {

        validatePortfolioOwnership(portfolioId, userId);

        return calculatePositionsInternal(portfolioId);
    }

    private List<PositionResponse> calculatePositionsInternal(
            Long portfolioId) {

        List<Transaction> transactions =
                transactionRepository
                        .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                                portfolioId
                        );

        Map<Asset, List<Transaction>> transactionsByAsset =
                transactions.stream()
                        .collect(Collectors.groupingBy(
                                Transaction::getAsset
                        ));

        return transactionsByAsset.entrySet()
                .stream()
                .map(entry ->
                        calculatePosition(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .toList();
    }

    private PositionResponse calculatePosition(
            Asset asset,
            List<Transaction> transactions) {

        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {

            if (transaction.getType() == TransactionType.BUY) {

                BigDecimal purchaseCost =
                        transaction.getQuantity()
                                .multiply(
                                        transaction.getUnitPrice()
                                );

                quantity =
                        quantity.add(
                                transaction.getQuantity()
                        );

                totalCost =
                        totalCost.add(purchaseCost);
            }

            if (transaction.getType() == TransactionType.SELL) {

                if (transaction.getQuantity()
                        .compareTo(quantity) > 0) {

                    throw new InsufficientPositionException(
                            asset.getTicker()
                    );
                }

                BigDecimal averagePrice =
                        totalCost.divide(
                                quantity,
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP
                        );

                BigDecimal soldCost =
                        transaction.getQuantity()
                                .multiply(averagePrice);

                quantity =
                        quantity.subtract(
                                transaction.getQuantity()
                        );

                totalCost =
                        totalCost.subtract(soldCost);

                if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                    totalCost = BigDecimal.ZERO;
                }
            }
        }

        BigDecimal averagePrice = BigDecimal.ZERO;

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {

            averagePrice =
                    totalCost.divide(
                            quantity,
                            DISPLAY_SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        BigDecimal displayedTotalCost =
                totalCost.setScale(
                        DISPLAY_SCALE,
                        RoundingMode.HALF_UP
                );

        return new PositionResponse(
                asset.getId(),
                asset.getTicker(),
                quantity,
                averagePrice,
                displayedTotalCost
        );
    }

    public PositionResponse calculatePositionByAsset(
            Long portfolioId,
            Long assetId) {

        return calculatePositionsInternal(portfolioId)
                .stream()
                .filter(position ->
                        position.assetId().equals(assetId)
                )
                .findFirst()
                .orElse(null);
    }

    public List<PositionMarketResponse> calculateMarketPositions(
            Long portfolioId,
            Long userId) {

        validatePortfolioOwnership(portfolioId, userId);

        return calculatePositionsInternal(portfolioId)
                .stream()
                .filter(position ->
                        position.quantity()
                                .compareTo(BigDecimal.ZERO) > 0
                )
                .map(position -> {

                    BigDecimal currentPrice =
                            quoteService.getCurrentPrice(
                                    position.assetTicker()
                            );

                    BigDecimal currentValue =
                            position.quantity()
                                    .multiply(currentPrice);

                    BigDecimal profitLoss =
                            currentValue.subtract(
                                    position.totalCost()
                            );

                    BigDecimal profitabilityPercent =
                            BigDecimal.ZERO;

                    if (position.totalCost()
                            .compareTo(BigDecimal.ZERO) > 0) {

                        profitabilityPercent =
                                profitLoss
                                        .multiply(
                                                BigDecimal.valueOf(100)
                                        )
                                        .divide(
                                                position.totalCost(),
                                                4,
                                                RoundingMode.HALF_UP
                                        );
                    }

                    return new PositionMarketResponse(
                            position.assetId(),
                            position.assetTicker(),
                            position.quantity(),
                            position.averagePrice(),
                            position.totalCost(),
                            currentPrice,
                            currentValue,
                            profitLoss,
                            profitabilityPercent
                    );
                })
                .toList();
    }

    private void validatePortfolioOwnership(
            Long portfolioId,
            Long userId) {

        portfolioRepository
                .findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(
                                portfolioId
                        ));
    }
}