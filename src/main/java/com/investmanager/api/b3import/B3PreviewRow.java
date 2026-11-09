package com.investmanager.api.b3import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Linha de negociação da B3, sem vínculos com carteira, ativo ou corretora internos. */
public record B3PreviewRow(
        int excelRow,
        LocalDate tradeDate,
        String movementType,
        String market,
        String expiry,
        String institution,
        String ticker,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        List<String> errors,
        List<String> warnings
) {
    public boolean valid() { return errors.isEmpty(); }
}
