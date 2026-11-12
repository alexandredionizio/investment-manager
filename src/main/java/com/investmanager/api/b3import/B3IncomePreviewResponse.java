package com.investmanager.api.b3import;

import java.util.List;

public record B3IncomePreviewResponse(String fileName, int totalRows, int incomeRows,
                                      int skippedRows, List<B3IncomePreviewRow> rows,
                                      List<SkippedMovement> skippedDetails) {
    public B3IncomePreviewResponse {
        rows = List.copyOf(rows);
        skippedDetails = List.copyOf(skippedDetails);
    }

    // Mantém compatibilidade com os consumidores e testes existentes.
    public B3IncomePreviewResponse(String fileName, int totalRows, int incomeRows,
                                   int skippedRows, List<B3IncomePreviewRow> rows) {
        this(fileName, totalRows, incomeRows, skippedRows, rows, List.of());
    }

    public record SkippedMovement(int lineNumber, String movement, String reason) {}
}
