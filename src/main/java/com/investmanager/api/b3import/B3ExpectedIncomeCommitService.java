package com.investmanager.api.b3import;

import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Confirmação atômica de previsões, nunca de pagamentos reais. */
@Service
public class B3ExpectedIncomeCommitService {
    private final B3ExpectedIncomePreviewService previews;
    private final B3ExpectedIncomeReconciliationService reconciliation;
    private final PortfolioRepository portfolios;
    private final JdbcTemplate jdbc;
    public B3ExpectedIncomeCommitService(B3ExpectedIncomePreviewService previews,
            B3ExpectedIncomeReconciliationService reconciliation, PortfolioRepository portfolios, JdbcTemplate jdbc) {
        this.previews = previews; this.reconciliation = reconciliation; this.portfolios = portfolios; this.jdbc = jdbc;
    }

    @Transactional
    public B3ExpectedIncomeCommitResponse commit(MultipartFile file, Long portfolioId, Long userId,
            B3ExpectedIncomeCommitRequest request) {
        if (request == null || !request.confirmed() || request.entries() == null
                || request.entries().isEmpty() || request.entries().size() > 1000)
            fail("Confirmação explícita de 1 a 1000 eventos é obrigatória.");
        if (portfolioId == null || userId == null || portfolios.findByIdAndUserId(portfolioId, userId).isEmpty())
            fail("Carteira não encontrada.");
        // Serializa importações da mesma carteira, inclusive em arquivos repetidos concorrentes.
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", (rs, n) -> rs.getString(1), portfolioId);
        var source = previews.preview(file);
        var checked = reconciliation.reconcile(file, portfolioId, userId);
        Map<Integer, B3ExpectedIncomePreviewRow> byLine = source.rows().stream()
                .collect(Collectors.toMap(B3ExpectedIncomePreviewRow::excelRow, Function.identity()));
        Map<Integer, B3ExpectedIncomeReconciliationResponse.Row> byCheck = checked.rows().stream()
                .collect(Collectors.toMap(B3ExpectedIncomeReconciliationResponse.Row::excelRow, Function.identity()));
        Set<Integer> uniqueLines = new HashSet<>(); Set<String> uniqueKeys = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var selection : request.entries()) {
            if (selection == null || !uniqueLines.add(selection.excelRow())) fail("Linha repetida na confirmação.");
            var row = byLine.get(selection.excelRow()); var check = byCheck.get(selection.excelRow());
            if (row == null || check == null || check.assetId() == null || !"READY".equals(check.status()))
                fail("Evento indisponível para importação na linha " + selection.excelRow());
            String key = B3ExpectedIncomeReconciliationService.fingerprint(portfolioId, check.assetId(), row);
            if (!uniqueKeys.add(key)) fail("Evento repetido no arquivo.");
            jdbc.update("insert into b3_expected_incomes (portfolio_id, asset_id, income_type, "
                      + "expected_payment_date, quantity, expected_net_amount, institution, fingerprint) "
                      + "values (?, ?, ?, ?, ?, ?, ?, ?)", portfolioId, check.assetId(), row.type(),
                    row.expectedPaymentDate(), row.quantity(), row.expectedNetAmount(), row.institution(), key);
            total = total.add(row.expectedNetAmount());
        }
        return new B3ExpectedIncomeCommitResponse(uniqueLines.size(), total);
    }
    private static void fail(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
