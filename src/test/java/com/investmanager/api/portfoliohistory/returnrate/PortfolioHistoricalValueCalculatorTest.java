package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfoliohistory.service.HistoricalPositionCalculator;
import com.investmanager.api.portfoliohistory.service.HistoricalQuoteResolver;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioHistoricalValueCalculatorTest {

    private HistoricalPositionCalculator positionCalculator;
    private HistoricalQuoteResolver quoteResolver;
    private PortfolioHistoricalValueCalculator calculator;

    @BeforeEach
    void setUp() {

        positionCalculator =
                mock(HistoricalPositionCalculator.class);

        quoteResolver =
                mock(HistoricalQuoteResolver.class);

        calculator =
                new PortfolioHistoricalValueCalculator(
                        positionCalculator,
                        quoteResolver
                );
    }

    @Test
    void shouldCalculatePortfolioHistoricalValue() {

        LocalDate date =
                LocalDate.of(2026, 9, 21);

        Asset asset1 = mock(Asset.class);
        Asset asset2 = mock(Asset.class);

        Transaction transaction1 =
                mock(Transaction.class);

        Transaction transaction2 =
                mock(Transaction.class);

        List<Transaction> transactions1 =
                List.of(transaction1);

        List<Transaction> transactions2 =
                List.of(transaction2);

        HistoricalQuote quote1 =
                mock(HistoricalQuote.class);

        HistoricalQuote quote2 =
                mock(HistoricalQuote.class);

        List<HistoricalQuote> quotes1 =
                List.of(quote1);

        List<HistoricalQuote> quotes2 =
                List.of(quote2);

        Map<Asset, List<Transaction>> transactionsByAsset =
                Map.of(
                        asset1, transactions1,
                        asset2, transactions2
                );

        Map<Asset, List<HistoricalQuote>> quotesByAsset =
                Map.of(
                        asset1, quotes1,
                        asset2, quotes2
                );

        when(positionCalculator.calculateQuantity(
                transactions1,
                date
        )).thenReturn(new BigDecimal("100"));

        when(positionCalculator.calculateQuantity(
                transactions2,
                date
        )).thenReturn(new BigDecimal("200"));

        when(quoteResolver.resolveClosePrice(
                quotes1,
                date
        )).thenReturn(new BigDecimal("35.00"));

        when(quoteResolver.resolveClosePrice(
                quotes2,
                date
        )).thenReturn(new BigDecimal("40.00"));

        BigDecimal result =
                calculator.calculate(
                        date,
                        transactionsByAsset,
                        quotesByAsset
                );

        assertEquals(
                new BigDecimal("11500.00"),
                result
        );
    }

    @Test
    void shouldIgnoreAssetWithoutPositivePosition() {

        LocalDate date =
                LocalDate.of(2026, 9, 21);

        Asset asset = mock(Asset.class);

        Transaction transaction =
                mock(Transaction.class);

        List<Transaction> transactions =
                List.of(transaction);

        Map<Asset, List<Transaction>> transactionsByAsset =
                Map.of(asset, transactions);

        Map<Asset, List<HistoricalQuote>> quotesByAsset =
                Map.of();

        when(positionCalculator.calculateQuantity(
                transactions,
                date
        )).thenReturn(BigDecimal.ZERO);

        BigDecimal result =
                calculator.calculate(
                        date,
                        transactionsByAsset,
                        quotesByAsset
                );

        assertEquals(
                BigDecimal.ZERO,
                result
        );
    }

    @Test
    void shouldIgnoreAssetWithoutHistoricalQuote() {

        LocalDate date =
                LocalDate.of(2026, 9, 21);

        Asset asset = mock(Asset.class);

        Transaction transaction =
                mock(Transaction.class);

        List<Transaction> transactions =
                List.of(transaction);

        HistoricalQuote quote =
                mock(HistoricalQuote.class);

        List<HistoricalQuote> quotes =
                List.of(quote);

        Map<Asset, List<Transaction>> transactionsByAsset =
                Map.of(asset, transactions);

        Map<Asset, List<HistoricalQuote>> quotesByAsset =
                Map.of(asset, quotes);

        when(positionCalculator.calculateQuantity(
                transactions,
                date
        )).thenReturn(new BigDecimal("100"));

        when(quoteResolver.resolveClosePrice(
                quotes,
                date
        )).thenReturn(null);

        BigDecimal result =
                calculator.calculate(
                        date,
                        transactionsByAsset,
                        quotesByAsset
                );

        assertEquals(
                BigDecimal.ZERO,
                result
        );
    }
}