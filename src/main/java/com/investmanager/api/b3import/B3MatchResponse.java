package com.investmanager.api.b3import;

import java.util.List;

/** Resultado de identificação somente para consulta; não autoriza persistência. */
public record B3MatchResponse(
        String fileName,
        int totalRows,
        int matchedRows,
        int reviewRows,
        int invalidRows,
        List<Row> rows
) {
    public record BrokerOption(Long id, String name) {}
    public record Row(
            int excelRow,
            String ticker,
            String institution,
            String assetStatus,
            Long assetId,
            String brokerStatus,
            Long brokerId,
            List<BrokerOption> brokerSuggestions,
            List<String> messages
    ) {}
}
