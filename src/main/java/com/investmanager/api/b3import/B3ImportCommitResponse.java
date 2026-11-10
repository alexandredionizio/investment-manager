package com.investmanager.api.b3import;

import java.util.List;

/** Resposta persistida e auditável de importação B3. */
public record B3ImportCommitResponse(
        Long batchId, Long portfolioId, String status, int importedRows, List<ImportedRow> rows) {
    public record ImportedRow(int excelRow, Long transactionId) {}
}
