package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfoliohistory.service.HistoricalPositionCalculator;
import com.investmanager.api.portfoliohistory.service.HistoricalQuoteResolver;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioHistoricalValueCalculator {

    private final HistoricalPositionCalculator positionCalculator;
    private final HistoricalQuoteResolver quoteResolver;

    public PortfolioHistoricalValueCalculator(
            HistoricalPositionCalculator positionCalculator,
            HistoricalQuoteResolver quoteResolver) {

        this.positionCalculator = positionCalculator;
        this.quoteResolver = quoteResolver;
    }

    public BigDecimal calculate(
            LocalDate date,
            Map<Asset, List<Transaction>> transactionsByAsset,
            Map<Asset, List<HistoricalQuote>> quotesByAsset) {

        BigDecimal portfolioValue = BigDecimal.ZERO;

        for (Map.Entry<Asset, List<Transaction>> entry
                : transactionsByAsset.entrySet()) {

            Asset asset = entry.getKey();

            List<Transaction> assetTransactions =
                    entry.getValue();

            BigDecimal quantity =
                    positionCalculator.calculateQuantity(
                            assetTransactions,
                            date
                    );

            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<HistoricalQuote> quotes =
                    quotesByAsset.get(asset);

            BigDecimal closePrice =
                    quoteResolver.resolveClosePrice(
                            quotes,
                            date
                    );

            if (closePrice == null) {
                continue;
            }

            BigDecimal assetValue =
                    quantity.multiply(closePrice);

            portfolioValue =
                    portfolioValue.add(assetValue);
        }

        return portfolioValue;
    }
}