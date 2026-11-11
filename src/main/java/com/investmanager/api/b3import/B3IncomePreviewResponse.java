package com.investmanager.api.b3import;

import java.util.List;

public record B3IncomePreviewResponse(String fileName, int totalRows, int incomeRows,
                                      int skippedRows, List<B3IncomePreviewRow> rows) {}
