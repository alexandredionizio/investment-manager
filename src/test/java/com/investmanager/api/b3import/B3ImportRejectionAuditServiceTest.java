package com.investmanager.api.b3import;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class B3ImportRejectionAuditServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final B3SpreadsheetPreviewService previews = mock(B3SpreadsheetPreviewService.class);
    private final B3ReconciliationService reconciliation = mock(B3ReconciliationService.class);
    private final B3ImportRejectionAuditService audit = new B3ImportRejectionAuditService(jdbc, previews, reconciliation);
    private final MockMultipartFile file = new MockMultipartFile("file", "movimentos.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

    @Test void rejectedAttemptPersistsOriginAndActualRowReasonsWithoutFinancialWrites() {
        B3PreviewRow invalid = new B3PreviewRow(4, LocalDate.of(2026, 9, 1), "Compra",
                "Mercado à Vista", "", "XP", "PETR4", BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.TEN, List.of("Quantidade inválida"), List.of());
        when(previews.preview(file)).thenReturn(new B3PreviewResponse("movimentos.xlsx", 1, 0, 1, List.of(invalid)));
        when(reconciliation.reconcile(file, 6L, 7L)).thenReturn(new B3ReconciliationResponse(
                "movimentos.xlsx", 6L, 1, 0, 0, 1, List.of(new B3ReconciliationResponse.Row(
                4, invalid.tradeDate(), "PETR4", "XP", "Compra", 1L, 2L,
                BigDecimal.ONE, BigDecimal.TEN, "BLOCKED", List.of("Quantidade inválida")))));
        when(jdbc.queryForObject(startsWith("INSERT INTO b3_import_rejected_attempts"),
                eq(Long.class), eq(6L), eq(7L), eq("movimentos.xlsx"), anyString(), eq("Conciliação recusada")))
                .thenReturn(91L);
        audit.record(file, 6L, 7L, "Conciliação recusada");
        verify(jdbc).update(startsWith("INSERT INTO b3_import_rejected_rows"),
                eq(91L), eq(4), eq("Quantidade inválida"));
        verify(jdbc, never()).update(contains("transactions"), any(Object[].class));
    }

    @Test void unreadableFileStillRecordsGlobalReasonWithoutInventingRowNumbers() {
        when(previews.preview(file)).thenThrow(new IllegalArgumentException("Cabeçalho inválido"));
        when(jdbc.queryForObject(startsWith("INSERT INTO b3_import_rejected_attempts"),
                eq(Long.class), any(), any(), any(), any(), any())).thenReturn(92L);
        audit.record(file, 6L, 7L, "Cabeçalho inválido");
        verify(jdbc, never()).update(startsWith("INSERT INTO b3_import_rejected_rows"), any(Object[].class));
    }

    @Test void commitConflictKeepsExcelRowEvenWhenReconciliationCannotRebuildIt() {
        String reason = "Linha 7 corresponde a negociação já presente em outro lote B3. Sem identificador de execução, revise manualmente.";
        when(jdbc.queryForObject(startsWith("INSERT INTO b3_import_rejected_attempts"),
                eq(Long.class), any(), any(), any(), any(), any())).thenReturn(93L);
        when(previews.preview(file)).thenThrow(new IllegalArgumentException("Prévia indisponível após rollback"));
        audit.record(file, 6L, 7L, reason);
        verify(jdbc).update(startsWith("INSERT INTO b3_import_rejected_rows"), eq(93L), eq(7), eq(reason));
        verify(jdbc, never()).update(contains("transactions"), any(Object[].class));
    }

    @Test void genericFailureRemainsGlobalWithoutFabricatedRow() {
        when(jdbc.queryForObject(startsWith("INSERT INTO b3_import_rejected_attempts"),
                eq(Long.class), any(), any(), any(), any(), any())).thenReturn(94L);
        when(previews.preview(file)).thenThrow(new IllegalArgumentException("arquivo indisponível"));
        audit.record(file, 6L, 7L, "Conciliação alterada ou pendente.");
        verify(jdbc, never()).update(startsWith("INSERT INTO b3_import_rejected_rows"), any(Object[].class));
    }
}
