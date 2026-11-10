package com.investmanager.api.b3import;

import java.time.OffsetDateTime;

public record B3ImportBatchSummary(Long batchId, Long portfolioId, String sourceFilename,
                                   OffsetDateTime importedAt, int rowCount) {}
