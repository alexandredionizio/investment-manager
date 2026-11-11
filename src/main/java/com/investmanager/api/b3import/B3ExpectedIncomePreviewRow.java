package com.investmanager.api.b3import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record B3ExpectedIncomePreviewRow(int excelRow, String product, String eventType,
        String type, LocalDate expectedPaymentDate, String institution, String account,
        BigDecimal quantity, BigDecimal unitPrice, BigDecimal expectedNetAmount,
        List<String> errors, List<String> warnings) {}
