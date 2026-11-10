package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class B3ReconciliationServiceTest {
    private final B3SpreadsheetPreviewService previews = mock(B3SpreadsheetPreviewService.class);
    private final B3MatchingService matches = mock(B3MatchingService.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final B3ReconciliationService service = new B3ReconciliationService(previews, matches, portfolios, transactions, jdbc);
    private final MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[]{1});
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private static final long PORTFOLIO = 6L;
    private static final long USER = 123L;

    @BeforeEach void owner() {
        when(portfolios.findByIdAndUserId(PORTFOLIO, USER)).thenReturn(Optional.of(mock(Portfolio.class)));
        when(transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(PORTFOLIO, USER)).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(PORTFOLIO), eq(USER), anyString()))
                .thenReturn(List.of());
    }
    private B3PreviewRow row(int line, String type, String ticker, String qty) {
        BigDecimal q = new BigDecimal(qty);
        return new B3PreviewRow(line, DAY, type, "Mercado à Vista", "", "XP", ticker,
                q, new BigDecimal("10"), q.multiply(new BigDecimal("10")), List.of(), List.of());
    }
    private void given(B3PreviewRow... rows) {
        when(previews.preview(file)).thenReturn(new B3PreviewResponse("test.xlsx", rows.length, rows.length, 0, List.of(rows)));
        var refs = java.util.Arrays.stream(rows).map(r -> new B3MatchResponse.Row(r.excelRow(), r.ticker(), "XP",
                "MATCHED", 1L, "MATCHED", 2L, List.of(), List.of())).toList();
        when(matches.match(file)).thenReturn(new B3MatchResponse("test.xlsx", rows.length, rows.length, 0, 0, refs));
    }
    private Transaction existing(String type, String qty) {
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(1L);
        Transaction t = new Transaction();
        t.setAsset(asset);
        t.setTransactionDate(DAY.minusDays(1));
        t.setType(TransactionType.valueOf(type));
        t.setQuantity(new BigDecimal(qty));
        t.setUnitPrice(new BigDecimal("10"));
        return t;
    }

    @Test void exactReimportShowsPreviouslyReversedRowAsBlockedWithoutSimulatingIt() {
        given(row(2, "Compra", "PETR4", "10"), row(3, "Venda", "PETR4", "4"));
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(PORTFOLIO), eq(USER), anyString()))
                .thenReturn(List.of(3));
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals(1, result.blockedRows());
        assertEquals(1, result.readyRows());
        assertEquals("BLOCKED", result.rows().get(1).status());
        assertTrue(result.rows().get(1).messages().stream().anyMatch(m -> m.contains("estornada")));
        verify(transactions, never()).save(any());
    }

    @Test void differentFileWithoutOriginalFingerprintDoesNotAssumeReversalFromMatchingTradeFields() {
        given(row(2, "Compra", "PETR4", "10"));
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals("READY", result.rows().get(0).status());
        assertEquals(0, result.blockedRows());
    }

    @Test void rejectsForeignPortfolioBeforeReadingFile() {
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(file, PORTFOLIO, 999L));
        verifyNoInteractions(previews, matches, transactions);
    }

    @Test void rejectsSaleWithoutPriorBalance() {
        given(row(2, "Venda", "PETR4", "2"));
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals(1, result.blockedRows());
        assertEquals("BLOCKED", result.rows().get(0).status());
        verify(transactions, never()).save(any());
    }

    @Test void existingBuyCanSupportSaleInReadOnlySimulation() {
        given(row(2, "Venda", "PETR4", "2"));
        Transaction compraExistente = existing("BUY", "3");
        when(transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(PORTFOLIO, USER))
                .thenReturn(List.of(compraExistente));
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals(1, result.readyRows());
        verify(transactions, never()).save(any());
    }

    @Test void duplicateInSpreadsheetRequiresReviewAndIsNotDiscarded() {
        given(row(2, "Compra", "PETR4", "1"), row(3, "Compra", "PETR4", "1"));
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals(2, result.reviewRows());
        assertEquals(2, result.rows().size());
    }

    @Test void buyThenSaleInSameDayRequiresReviewBecauseTimeUnknown() {
        given(row(2, "Compra", "PETR4", "3"), row(3, "Venda", "PETR4", "2"));
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals(2, result.reviewRows());
        assertEquals(0, result.blockedRows());
    }

    @Test void chronologicallyEarlierSaleIsBlockedEvenIfLaterBuyExists() {
        var earlySale = row(2, "Venda", "PETR4", "2");
        var lateBuy = new B3PreviewRow(3, DAY.plusDays(1), "Compra", "Mercado à Vista", "", "XP", "PETR4",
                new BigDecimal("3"), BigDecimal.TEN, new BigDecimal("30"), List.of(), List.of());
        given(earlySale, lateBuy);
        var result = service.reconcile(file, PORTFOLIO, USER);
        assertEquals(1, result.blockedRows());
        assertEquals("BLOCKED", result.rows().get(0).status());
    }
}
