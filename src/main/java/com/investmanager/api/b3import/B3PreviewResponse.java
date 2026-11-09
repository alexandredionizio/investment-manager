package com.investmanager.api.b3import;

import java.util.List;

public record B3PreviewResponse(
        String fileName,
        int totalRows,
        int validRows,
        int invalidRows,
        List<B3PreviewRow> rows
) {}
