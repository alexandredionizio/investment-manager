package com.investmanager.api.b3import;

import java.util.List;

public record B3ExpectedIncomePreviewResponse(String fileName, int totalRows,
        int recognizedRows, int skippedRows, List<B3ExpectedIncomePreviewRow> rows) {}
