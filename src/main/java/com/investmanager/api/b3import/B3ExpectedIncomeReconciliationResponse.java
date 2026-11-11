package com.investmanager.api.b3import;
import java.util.List;
public record B3ExpectedIncomeReconciliationResponse(Long portfolioId, int total, int ready, int blocked,
        List<Row> rows) {
    public record Row(int excelRow, String ticker, Long assetId, String status, List<String> messages) {}
}
