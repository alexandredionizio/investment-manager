package com.investmanager.api.b3import;

import java.util.List;

public record B3IncomeCommitRequest(boolean confirmed, List<Entry> entries) {
    public record Entry(int excelRow, boolean confirmAmountDifference) {
        public Entry(int excelRow) { this(excelRow, false); }
    }
}
