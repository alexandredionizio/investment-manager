package com.investmanager.api.b3import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Extrato B3: data do crédito, não a data-base societária. Somente leitura. */
public record B3IncomePreviewRow(
        int excelRow, LocalDate paymentDate, String movement, String product,
        String institution, String type, BigDecimal quantity,
        BigDecimal unitPrice, BigDecimal creditedAmount,
        List<String> errors, List<String> warnings
) {}
