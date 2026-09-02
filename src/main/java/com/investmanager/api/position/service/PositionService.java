package com.investmanager.api.position.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.exception.InsufficientPositionException;
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

    private final TransactionRepository transactionRepository;

    private final PortfolioRepository portfolioRepository;

    public PositionService(
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository) {

        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public List<PositionResponse> calculatePositions(Long portfolioId) {

        if (!portfolioRepository.existsById(portfolioId)) {
            throw new PortfolioNotFoundException(portfolioId);
        }

        List<Transaction> transactions =
                transactionRepository.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);

        Map<Asset, List<Transaction>> transactionsByAsset =
                transactions.stream()
                        .collect(Collectors.groupingBy(Transaction::getAsset));

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
            List<Transaction> transactions){

        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Transaction transaction: transactions) {

            if (transaction.getType() == TransactionType.BUY) {

                BigDecimal purchaseCost =
                        transaction.getQuantity()
                                .multiply(transaction.getUnitPrice());

                quantity = quantity.add(transaction.getQuantity());
                totalCost = totalCost.add(purchaseCost);
            }

            if (transaction.getType() == TransactionType.SELL) {

                if (transaction.getQuantity().compareTo(quantity) > 0 ) {
                    throw new InsufficientPositionException(asset.getTicker());
                }

                BigDecimal averagePrice =
                        totalCost.divide(quantity, 2, RoundingMode.HALF_UP);

                BigDecimal soldCost =
                        transaction.getQuantity()
                                .multiply(averagePrice);

                quantity = quantity.subtract(transaction.getQuantity());
                totalCost = totalCost.subtract(soldCost);

                if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                    totalCost = BigDecimal.ZERO;
                }
            }
        }

        BigDecimal averagePrice = BigDecimal.ZERO;

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            averagePrice = totalCost.divide(
                    quantity,
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return new PositionResponse(
                asset.getId(),
                asset.getTicker(),
                quantity,
                averagePrice,
                totalCost
        );
    }

    public PositionResponse calculatePositionByAsset(
            Long portfolioId,
            Long assetId) {

        return calculatePositions(portfolioId)
                .stream()
                .filter(position -> position.assetId().equals(assetId))
                .findFirst()
                .orElse(null);
    }
}
