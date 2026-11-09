package com.investmanager.api.b3import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** IM-041: análise informativa; nunca autoriza uma importação. */
public record B3ReconciliationResponse(
        String fileName, Long portfolioId, int totalRows,
        int readyRows, int reviewRows, int blockedRows, List<Row> rows) {
    public record Row(int excelRow, LocalDate tradeDate, String ticker, String institution,
                      String movementType, Long assetId, Long brokerId,
                      BigDecimal quantity, BigDecimal unitPrice,
                      String status, List<String> messages) {}
}
