package com.investmanager.api.realizedresult.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.realizedresult.dto.RealizedResultResponse;
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
public class RealizedResultService {

    private static final int CALCULATION_SCALE = 12;
    private static final int DISPLAY_SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;

    public RealizedResultService(
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository) {

        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public List<RealizedResultResponse> calculateRealizedResults(
            Long portfolioId,
            Long userId) {

        validatePortfolioOwnership(
                portfolioId,
                userId
        );

        List<Transaction> transactions =
                transactionRepository
                        .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                                portfolioId,
                                userId
                        );

        Map<Asset, List<Transaction>> transactionsByAsset =
                transactions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Transaction::getAsset
                                )
                        );

        return transactionsByAsset
                .entrySet()
                .stream()
                .map(entry ->
                        calculateRealizedResult(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .filter(result ->
                        result.soldQuantity()
                                .compareTo(BigDecimal.ZERO) > 0
                )
                .toList();
    }

    public RealizedResultResponse calculateRealizedResult(
            Asset asset,
            List<Transaction> transactions) {

        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        BigDecimal soldQuantity = BigDecimal.ZERO;
        BigDecimal totalSaleValue = BigDecimal.ZERO;
        BigDecimal totalSoldCost = BigDecimal.ZERO;
        BigDecimal realizedProfitLoss = BigDecimal.ZERO;

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

                BigDecimal saleValue =
                        transaction.getQuantity()
                                .multiply(
                                        transaction.getUnitPrice()
                                );

                BigDecimal soldCost =
                        transaction.getQuantity()
                                .multiply(averagePrice);

                BigDecimal saleProfitLoss =
                        saleValue.subtract(soldCost);

                soldQuantity =
                        soldQuantity.add(
                                transaction.getQuantity()
                        );

                totalSaleValue =
                        totalSaleValue.add(saleValue);

                totalSoldCost =
                        totalSoldCost.add(soldCost);

                realizedProfitLoss =
                        realizedProfitLoss.add(
                                saleProfitLoss
                        );

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

        return new RealizedResultResponse(
                asset.getId(),
                asset.getTicker(),
                soldQuantity,
                totalSaleValue.setScale(
                        DISPLAY_SCALE,
                        RoundingMode.HALF_UP
                ),
                totalSoldCost.setScale(
                        DISPLAY_SCALE,
                        RoundingMode.HALF_UP
                ),
                realizedProfitLoss.setScale(
                        DISPLAY_SCALE,
                        RoundingMode.HALF_UP
                )
        );
    }

    private void validatePortfolioOwnership(
            Long portfolioId,
            Long userId) {

        portfolioRepository
                .findByIdAndUserId(
                        portfolioId,
                        userId
                )
                .orElseThrow(() ->
                        new PortfolioNotFoundException(
                                portfolioId
                        ));
    }
}