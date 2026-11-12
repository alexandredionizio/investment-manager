package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.service.TransactionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class B3ImportCommitServiceTest {
    private static final Long PORTFOLIO = 6L, USER = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private final B3SpreadsheetPreviewService previews = mock(B3SpreadsheetPreviewService.class);
    private final B3MatchingService matching = mock(B3MatchingService.class);
    private final B3ReconciliationService reconciliation = mock(B3ReconciliationService.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final PortfolioAssetLockManager locks = mock(PortfolioAssetLockManager.class);
    private final TransactionCreationExecutor executor = mock(TransactionCreationExecutor.class);
    private final TransactionService transactions = mock(TransactionService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final B3ImportRejectionAuditService rejectionAudit = mock(B3ImportRejectionAuditService.class);
    private final B3ImportCommitService service = new B3ImportCommitService(
            previews, matching, reconciliation, portfolios, assets, locks, executor, transactions, em, jdbc, rejectionAudit);
    private final MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[] {1});

    @BeforeEach void setup() {
        when(executor.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        Portfolio ownedPortfolio = mock(Portfolio.class);
        when(portfolios.findByIdAndUserId(PORTFOLIO, USER)).thenReturn(Optional.of(ownedPortfolio));
        Asset registeredAsset = mock(Asset.class);
        when(registeredAsset.getType()).thenReturn(AssetType.STOCK);
        when(registeredAsset.getTicker()).thenReturn("PETR4");
        when(registeredAsset.getExchange()).thenReturn("B3");
        when(assets.findById(1L)).thenReturn(Optional.of(registeredAsset));
    }

    private B3PreviewRow row(int index, String type, String qty) {
        BigDecimal amount = new BigDecimal(qty);
        return new B3PreviewRow(index, DAY, type, "Mercado à Vista", "", "XP", "PETR4",
                amount, BigDecimal.TEN, amount.multiply(BigDecimal.TEN), List.of(), List.of());
    }

    private void given(B3PreviewRow... rows) {
        when(previews.preview(file)).thenReturn(new B3PreviewResponse("test.xlsx", rows.length, rows.length, 0, List.of(rows)));
        var links = java.util.Arrays.stream(rows)
                .map(r -> new B3MatchResponse.Row(r.excelRow(), r.ticker(), "XP", "MATCHED", 1L,
                        "MATCHED", 2L, List.of(), List.of())).toList();
        when(matching.match(file)).thenReturn(new B3MatchResponse("test.xlsx", rows.length, rows.length, 0, 0, links));
        var reconciled = java.util.Arrays.stream(rows)
                .map(r -> new B3ReconciliationResponse.Row(r.excelRow(), r.tradeDate(), r.ticker(), "XP",
                        r.movementType(), 1L, 2L, r.quantity(), r.unitPrice(), "READY", List.of())).toList();
        when(reconciliation.reconcile(file, PORTFOLIO, USER))
                .thenReturn(new B3ReconciliationResponse("test.xlsx", PORTFOLIO, rows.length, rows.length, 0, 0, reconciled));
        // JdbcTemplate query() vazio significa importação ainda não realizada.
        when(jdbc.query(anyString(), any(RowMapper.class), eq(PORTFOLIO), anyString())).thenReturn(List.of());
        when(jdbc.queryForObject(startsWith("SELECT count(*) FROM b3_transaction_reversals"),
                eq(Long.class), eq(PORTFOLIO), eq(1L), eq(2L), anyString(), eq(DAY),
                any(BigDecimal.class), eq(BigDecimal.TEN))).thenReturn(0L);
    }

    @Test void confirmationIsMandatoryAndDoesNotAccessDatabase() {
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, USER, false));
        verifyNoInteractions(previews, matching, reconciliation, jdbc, transactions);
    }

    @Test void invalidPreviewRowsCannotReachMatchingOrPersistence() {
        when(previews.preview(file)).thenReturn(new B3PreviewResponse(
                "test.xlsx", 1, 0, 1, List.of()));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.commit(file, PORTFOLIO, USER, true));
        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(matching, reconciliation, transactions, locks, jdbc);
    }

    @Test void foreignPortfolioIsRejectedBeforeParsing() {
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, 999L, true));
        verifyNoInteractions(previews, matching, reconciliation, jdbc, transactions);
    }

    @Test void blockedReconciliationCannotPersistAnything() {
        given(row(2, "Venda", "10"));
        when(reconciliation.reconcile(file, PORTFOLIO, USER))
                .thenReturn(new B3ReconciliationResponse("test.xlsx", PORTFOLIO, 1, 0, 0, 1, List.of()));
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, USER, true));
        verify(transactions, never()).createConfirmed(any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void reviewCannotPersistAnythingEvenIfRowIsIdentified() {
        given(row(2, "Compra", "10"));
        when(reconciliation.reconcile(file, PORTFOLIO, USER))
                .thenReturn(new B3ReconciliationResponse("test.xlsx", PORTFOLIO, 1, 0, 1, 0, List.of()));
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, USER, true));
        verify(transactions, never()).createConfirmed(any(), any(), any());
    }

    @Test void successfulBatchUsesExistingAccountingServiceAndLinksRows() {
        given(row(2, "Compra", "3"));
        when(jdbc.queryForObject(startsWith("INSERT INTO b3_import_batches"), eq(Long.class),
                eq(PORTFOLIO), eq(USER), anyString(), eq("test.xlsx"), eq(1)))
                .thenReturn(91L);
        when(transactions.createConfirmed(any(), eq(USER), eq("PETR4")))
                .thenReturn(new TransactionResponse(84L, PORTFOLIO, 1L, "PETR4", 2L, "XP",
                        com.investmanager.api.transaction.TransactionType.BUY,
                        new BigDecimal("3"), BigDecimal.TEN, DAY));
        var result = service.commit(file, PORTFOLIO, USER, true);
        assertEquals("IMPORTED", result.status());
        assertEquals(1, result.importedRows());
        assertEquals(84L, result.rows().get(0).transactionId());
        verify(locks).acquire(PORTFOLIO, 1L);
        verify(transactions).createConfirmed(any(), eq(USER), eq("PETR4"));
        verify(jdbc).update(startsWith("INSERT INTO b3_import_batch_rows"), eq(91L), eq(2), eq(84L));
    }

    @Test void fingerprintIsIndependentOfFilenameAndDecimalScale() {
        B3PreviewRow first = row(2, "Compra", "3.00");
        B3PreviewRow second = row(2, "Compra", "3.0");
        var matchedRow = new B3MatchResponse.Row(2, "PETR4", "XP", "MATCHED", 1L,
                "MATCHED", 2L, List.of(), List.of());
        String original = B3ImportCommitService.fingerprint(
                new B3PreviewResponse("arquivo-original.xlsx", 1, 1, 0, List.of(first)),
                java.util.Map.of(2, matchedRow));
        String renamed = B3ImportCommitService.fingerprint(
                new B3PreviewResponse("arquivo-renomeado.xlsx", 1, 1, 0, List.of(second)),
                java.util.Map.of(2, matchedRow));
        assertEquals(original, renamed, "Renomear arquivo ou mudar escala decimal não pode duplicar lote");
        assertEquals(64, original.length());
    }

    @Test void reimportReturnsOriginalBatchWithoutCreatingAnotherTransaction() {
        given(row(2, "Compra", "3"));
        when(jdbc.query(startsWith("SELECT id, row_count"), any(RowMapper.class),
                eq(PORTFOLIO), anyString())).thenReturn(List.of(
                new B3ImportCommitResponse(91L, PORTFOLIO, "ALREADY_IMPORTED", 1, List.of())));
        when(jdbc.query(startsWith("SELECT excel_row"), any(RowMapper.class), eq(91L)))
                .thenReturn(List.of(new B3ImportCommitResponse.ImportedRow(2, 84L)));
        var response = service.commit(file, PORTFOLIO, USER, true);
        assertEquals("ALREADY_IMPORTED", response.status());
        assertEquals(0, response.importedRows());
        assertEquals(84L, response.rows().get(0).transactionId());
        verifyNoInteractions(locks, reconciliation, transactions);
    }

    @Test void partiallyOverlappingBatchRequiresManualReviewBeforeAccounting() {
        given(row(2, "Compra", "3"), row(3, "Compra", "2"));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM b3_import_batch_rows"),
                eq(Long.class), eq(PORTFOLIO), eq(USER), eq(1L), eq(2L), eq("BUY"), eq(DAY),
                eq(new BigDecimal("3")), eq(BigDecimal.TEN))).thenReturn(1L);
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.commit(file, PORTFOLIO, USER, true));
        assertEquals(409, failure.getStatusCode().value());
        assertTrue(failure.getReason().contains("revise manualmente"));
        verify(transactions, never()).createConfirmed(any(), any(), any());
        verify(jdbc, never()).update(startsWith("INSERT INTO b3_import_batch_rows"), any(), any(), any());
    }

    @Test void reversedTradeCannotBeSilentlyReimportedWithChangedFingerprint() {
        given(row(2, "Compra", "3"));
        when(jdbc.queryForObject(startsWith("SELECT count(*) FROM b3_transaction_reversals"),
                eq(Long.class), eq(PORTFOLIO), eq(1L), eq(2L), eq("BUY"), eq(DAY),
                eq(new BigDecimal("3")), eq(BigDecimal.TEN))).thenReturn(1L);
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.commit(file, PORTFOLIO, USER, true));
        assertEquals(409, failure.getStatusCode().value());
        verify(transactions, never()).createConfirmed(any(), any(), any());
    }

    @Test void batchHistoryIsRestrictedToPortfolioOwner() {
        assertThrows(ResponseStatusException.class, () -> service.batches(PORTFOLIO, 999L));
        verifyNoInteractions(jdbc);
    }

    @Test void duplicateInBatchIsRejectedBeforeTransactions() {
        given(row(2, "Compra", "2"), row(3, "Compra", "2"));
        when(reconciliation.reconcile(file, PORTFOLIO, USER))
                .thenReturn(new B3ReconciliationResponse("test.xlsx", PORTFOLIO, 2, 0, 2, 0, List.of()));
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, USER, true));
        verify(transactions, never()).createConfirmed(any(), any(), any());
    }
    @Test void confirmedHistoricalTickerCanBeCommittedWithOriginalRecordedTicker() {
        B3PreviewRow historic = new B3PreviewRow(2, DAY, "Compra", "Mercado à Vista", "", "XP", "PETR3",
                new BigDecimal("3"), BigDecimal.TEN, new BigDecimal("30"), List.of(), List.of());
        given(historic);
        Asset confirmed = mock(Asset.class);
        when(confirmed.getId()).thenReturn(1L);
        when(assets.findByNormalizedTicker("PETR3")).thenReturn(List.of());
        when(assets.findByConfirmedB3Ticker("PETR3")).thenReturn(List.of(confirmed));
        when(jdbc.queryForObject(startsWith("INSERT INTO b3_import_batches"), eq(Long.class),
                eq(PORTFOLIO), eq(USER), anyString(), eq("test.xlsx"), eq(1)))
                .thenReturn(91L);
        when(transactions.createConfirmed(any(), eq(USER), eq("PETR3")))
                .thenReturn(new TransactionResponse(84L, PORTFOLIO, 1L, "PETR4", 2L, "XP",
                        com.investmanager.api.transaction.TransactionType.BUY,
                        new BigDecimal("3"), BigDecimal.TEN, DAY));
        var response = service.commit(file, PORTFOLIO, USER, true);
        assertEquals("IMPORTED", response.status());
        verify(transactions).createConfirmed(any(), eq(USER), eq("PETR3"));
    }

    @Test void ambiguousHistoricalTickerCannotBeCommittedEvenIfPreviewSaysMatched() {
        B3PreviewRow historic = new B3PreviewRow(2, DAY, "Compra", "Mercado à Vista", "", "XP", "PETR3",
                new BigDecimal("3"), BigDecimal.TEN, new BigDecimal("30"), List.of(), List.of());
        given(historic);
        Asset first = mock(Asset.class);
        Asset second = mock(Asset.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(99L);
        when(assets.findByConfirmedB3Ticker("PETR3")).thenReturn(List.of(first, second));
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.commit(file, PORTFOLIO, USER, true));
        assertEquals(409, exception.getStatusCode().value());
        verify(transactions, never()).createConfirmed(any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void unconfirmedHistoricalTickerCannotBeCommitted() {
        B3PreviewRow historic = new B3PreviewRow(2, DAY, "Compra", "Mercado à Vista", "", "XP", "PETR3",
                new BigDecimal("3"), BigDecimal.TEN, new BigDecimal("30"), List.of(), List.of());
        given(historic);
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.commit(file, PORTFOLIO, USER, true));
        assertEquals(409, exception.getStatusCode().value());
        verify(transactions, never()).createConfirmed(any(), any(), any());
    }

    @Test void rejectedConfirmedAttemptIsAuditedWithoutCreatingTransactions() {
        given(row(2, "Compra", "10"));
        when(reconciliation.reconcile(file, PORTFOLIO, USER))
                .thenReturn(new B3ReconciliationResponse("test.xlsx", PORTFOLIO, 1, 0, 1, 0, List.of()));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.commit(file, PORTFOLIO, USER, true));
        assertEquals(409, error.getStatusCode().value());
        verify(rejectionAudit).record(eq(file), eq(PORTFOLIO), eq(USER), contains("Conciliação"));
        verify(transactions, never()).createConfirmed(any(), any(), any());
    }

    @Test void unconfirmedOrForeignImportMustNotBeAudited() {
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, USER, false));
        assertThrows(ResponseStatusException.class, () -> service.commit(file, PORTFOLIO, 999L, true));
        verifyNoInteractions(rejectionAudit);
    }

}
