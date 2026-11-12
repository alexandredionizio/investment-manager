package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaleOptionsServiceTest {
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final SaleOptionsService service = new SaleOptionsService(portfolios, transactions);
    private final LocalDate date = LocalDate.of(2026, 9, 10);

    private Transaction transaction(Asset asset, TransactionType type, String amount, LocalDate day) {
        Transaction t = mock(Transaction.class);
        when(t.getAsset()).thenReturn(asset);
        when(t.getType()).thenReturn(type);
        when(t.getQuantity()).thenReturn(new BigDecimal(amount));
        when(t.getTransactionDate()).thenReturn(day);
        return t;
    }

    private Asset asset() {
        Asset a = mock(Asset.class);
        when(a.getId()).thenReturn(3L);
        when(a.getTicker()).thenReturn("PETR4");
        when(a.getName()).thenReturn("Petrobras");
        when(a.getType()).thenReturn(AssetType.STOCK);
        return a;
    }

    private void owned(Transaction... history) {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolios.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(portfolio));
        when(transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(1L, 7L))
                .thenReturn(List.of(history));
    }

    @Test void futureSaleUsingEntireBalanceMustNotBeListedForRetroactiveSale() {
        var a = asset();
        owned(transaction(a, TransactionType.BUY, "10", date.minusDays(2)),
                transaction(a, TransactionType.SELL, "10", date.plusDays(2)));
        assertTrue(service.find(1L, 7L, date).isEmpty());
    }

    @Test void saleOptionReportsChronologicalLimitNotJustBalanceAtDate() {
        var a = asset();
        owned(transaction(a, TransactionType.BUY, "10", date.minusDays(2)),
                transaction(a, TransactionType.SELL, "7", date.plusDays(2)));
        var options = service.find(1L, 7L, date);
        assertEquals(1, options.size());
        assertEquals(0, options.getFirst().quantityAtDate().compareTo(new BigDecimal("10")));
        assertEquals(0, options.getFirst().chronologicalLimit().compareTo(new BigDecimal("3")));
    }

    @Test void purchaseAfterChosenDateDoesNotMakeSaleEligible() {
        var a = asset();
        owned(transaction(a, TransactionType.BUY, "10", date.plusDays(1)));
        assertTrue(service.find(1L, 7L, date).isEmpty());
    }

    @Test void intermediateFutureSaleLimitsQuantityEvenAfterLaterPurchase() {
        var a = asset();
        owned(transaction(a, TransactionType.BUY, "12", date.minusDays(2)),
                transaction(a, TransactionType.SELL, "9", date.plusDays(1)),
                transaction(a, TransactionType.BUY, "20", date.plusDays(2)));
        var options = service.find(1L, 7L, date);
        assertEquals(1, options.size());
        assertEquals(0, options.getFirst().quantityAtDate().compareTo(new BigDecimal("12")));
        assertEquals(0, options.getFirst().chronologicalLimit().compareTo(new BigDecimal("3")),
                "Compras futuras não podem financiar uma venda retroativa anterior à compra");
    }

    @Test void userCannotSearchSaleOptionsForForeignPortfolio() {
        assertThrows(RuntimeException.class, () -> service.find(1L, 99L, date));
        verifyNoInteractions(transactions);
    }
}
