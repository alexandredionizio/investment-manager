package com.investmanager.api.b3import;

import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conciliação de eventos anunciados. Nunca altera o histórico de créditos. */
@Service
public class B3ExpectedIncomeReconciliationService {
    private static final Pattern TICKER = Pattern.compile("(?:^|[^A-Z0-9])([A-Z]{4}[0-9]{1,2}[A-Z]?)(?=$|[^A-Z0-9])");
    private final B3ExpectedIncomePreviewService previews;
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final IncomeRepository incomes;
    private final JdbcTemplate jdbc;

    public B3ExpectedIncomeReconciliationService(B3ExpectedIncomePreviewService previews,
            PortfolioRepository portfolios, AssetRepository assets, IncomeRepository incomes, JdbcTemplate jdbc) {
        this.previews = previews; this.portfolios = portfolios; this.assets = assets; this.incomes = incomes; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public B3ExpectedIncomeReconciliationResponse reconcile(MultipartFile file, Long portfolioId, Long userId) {
        if (portfolioId == null || userId == null || portfolios.findByIdAndUserId(portfolioId, userId).isEmpty())
            throw new IllegalArgumentException("Carteira não encontrada ou não pertence ao usuário.");
        var preview = previews.preview(file);
        var output = new ArrayList<B3ExpectedIncomeReconciliationResponse.Row>();
        int ready = 0;
        for (var r : preview.rows()) {
            String ticker = ticker(r.product());
            Long assetId = null;
            var messages = new ArrayList<>(r.errors());
            if (r.expectedPaymentDate() == null || r.quantity() == null || r.expectedNetAmount() == null)
                messages.add("Dados obrigatórios ausentes.");
            if (ticker == null) messages.add("Ticker ausente ou ambíguo.");
            if (ticker != null) {
                var matched = assets.findByNormalizedTicker(ticker);
                if (matched.size() != 1) messages.add(matched.isEmpty() ? "Ativo não cadastrado: " + ticker : "Ticker ambíguo: " + ticker);
                else assetId = matched.get(0).getId();
            }
            if (messages.isEmpty()) {
                String key = fingerprint(portfolioId, assetId, r);
                Integer count = jdbc.queryForObject("select count(*) from b3_expected_incomes where portfolio_id = ? and fingerprint = ?",
                        Integer.class, portfolioId, key);
                if (count != null && count > 0) messages.add("Evento previsto já importado.");
                // Um recebimento já confirmado não pode ser importado novamente como previsão.
                boolean received = incomes.findByPortfolioIdAndAssetIdOrderByBaseDateAscIdAsc(portfolioId, assetId)
                        .stream().anyMatch(i -> i.getType() != null && i.getType().name().equals(r.type())
                                && i.getPaymentDate() != null && i.getPaymentDate().equals(r.expectedPaymentDate())
                                && i.getQuantity() != null && i.getQuantity().compareTo(r.quantity()) == 0
                                && i.getTotalAmount() != null
                                && i.getTotalAmount().subtract(r.expectedNetAmount()).abs()
                                       .compareTo(new BigDecimal("0.01")) <= 0);
                if (received) messages.add("Há um crédito recebido semelhante; exige análise manual.");
            }
            String status = messages.isEmpty() ? "READY" : "BLOCKED";
            if ("READY".equals(status)) ready++;
            output.add(new B3ExpectedIncomeReconciliationResponse.Row(r.excelRow(), ticker, assetId,
                    status, List.copyOf(messages)));
        }
        return new B3ExpectedIncomeReconciliationResponse(portfolioId, output.size(), ready,
                output.size() - ready, List.copyOf(output));
    }

    public static String fingerprint(Long portfolioId, Long assetId, B3ExpectedIncomePreviewRow row) {
        String institution = normalize(row.institution());
        String account = normalize(row.account());
        String input = portfolioId + "|" + assetId + "|" + row.type() + "|" + row.expectedPaymentDate()
                + "|" + row.quantity().stripTrailingZeros().toPlainString() + "|"
                + row.expectedNetAmount().stripTrailingZeros().toPlainString() + "|" + institution + "|" + account;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    static String ticker(String product) {
        var matcher = TICKER.matcher(normalize(product));
        if (!matcher.find()) return null;
        String first = matcher.group(1);
        return matcher.find() ? null : first;
    }
}
