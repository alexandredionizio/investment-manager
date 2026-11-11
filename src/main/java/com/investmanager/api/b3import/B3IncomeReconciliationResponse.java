package com.investmanager.api.b3import;

import java.util.List;
import java.math.BigDecimal;

/** Resultado somente leitura: nenhum lançamento é criado nesta etapa. */
public record B3IncomeReconciliationResponse(
        Long portfolioId, int total, int identified, int needsReview, int blocked,
        List<Row> rows) {
    public record Row(int excelRow, String ticker, Long assetId, String status,
                      List<String> messages, Long expectedIncomeId, BigDecimal expectedAmount) {
        public Row(int excelRow, String ticker, Long assetId, String status, List<String> messages) {
            this(excelRow, ticker, assetId, status, messages, null, null);
        }
    }
}
