package com.investmanager.api.b3import;

import java.math.BigDecimal;
import java.util.List;

public record B3IncomeCommitResponse(int imported, BigDecimal totalCredited, List<Long> incomeIds) {}
