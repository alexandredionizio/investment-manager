package com.investmanager.api.b3import;

import java.util.List;

public record B3IncomeCommitRequest(boolean confirmed, List<Entry> entries) {
    public record Entry(int excelRow) {}
}
