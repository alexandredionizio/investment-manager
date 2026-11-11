package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** IM-054: correspondência de ativos e suspeitas de repetição, SEM GRAVAÇÃO. */
@Service
public class B3IncomeReconciliationService {
    private static final Pattern TICKER = Pattern.compile("(?:^|[^A-Z0-9])([A-Z]{4}[0-9]{1,2}[A-Z]?)(?=$|[^A-Z0-9])");
    private final B3IncomePreviewService previews;
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final IncomeRepository incomes;

    public B3IncomeReconciliationService(B3IncomePreviewService previews, PortfolioRepository portfolios,
                                         AssetRepository assets, IncomeRepository incomes) {
        this.previews = previews;
        this.portfolios = portfolios;
        this.assets = assets;
        this.incomes = incomes;
    }

    @Transactional(readOnly = true)
    public B3IncomeReconciliationResponse reconcile(MultipartFile file, Long portfolioId, Long userId) {
        if (portfolioId == null || userId == null || portfolios.findByIdAndUserId(portfolioId, userId).isEmpty())
            throw new IllegalArgumentException("Carteira não encontrada ou não pertence ao usuário autenticado.");
        var preview = previews.preview(file);
        var rows = new ArrayList<B3IncomeReconciliationResponse.Row>();
        int identified = 0, blocked = 0, review = 0;
        for (var row : preview.rows()) {
            String ticker = ticker(row.product());
            Long assetId = null;
            String status;
            List<String> messages = new ArrayList<>(row.errors());
            if (!messages.isEmpty() || row.paymentDate() == null || row.quantity() == null || row.creditedAmount() == null) {
                status = "BLOCKED";
            } else if (ticker == null) {
                status = "BLOCKED";
                messages.add("Não foi possível identificar um ticker inequívoco no produto B3.");
            } else {
                List<Asset> matches = assets.findByNormalizedTicker(ticker);
                if (matches.size() != 1) {
                    status = "BLOCKED";
                    messages.add(matches.isEmpty() ? "Ativo não cadastrado: " + ticker : "Ticker ambíguo: " + ticker);
                } else {
                    Asset asset = matches.get(0);
                    assetId = asset.getId();
                    identified++;
                    // Igualdade de valores é apenas suspeita: não há ID do pagamento no extrato.
                    boolean suspected = incomes.findByPortfolioIdAndAssetIdOrderByBaseDateAscIdAsc(portfolioId, assetId)
                            .stream().anyMatch(existing -> samePayment(existing, row));
                    status = suspected ? "POSSIBLE_DUPLICATE" : "READY";
                    messages.add(suspected
                            ? "Possível duplicidade: pagamento semelhante já registrado. Exige conferência."
                            : "Crédito registrado na B3; confirme o valor recebido antes de importar.");
                    if (row.unitPrice() != null && row.quantity().multiply(row.unitPrice())
                            .subtract(row.creditedAmount()).abs().compareTo(new BigDecimal("0.02")) > 0)
                        messages.add("Valor creditado diferente do bruto calculado; preservar o líquido recebido.");
                }
            }
            if ("BLOCKED".equals(status)) blocked++; else review++;
            rows.add(new B3IncomeReconciliationResponse.Row(row.excelRow(), ticker, assetId, status, List.copyOf(messages)));
        }
        return new B3IncomeReconciliationResponse(portfolioId, rows.size(), identified, review, blocked, List.copyOf(rows));
    }

    private static String ticker(String product) {
        String normalized = Normalizer.normalize(product == null ? "" : product, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        var matcher = TICKER.matcher(normalized);
        if (!matcher.find()) return null;
        String first = matcher.group(1);
        return matcher.find() ? null : first; // mais de um ticker: exige intervenção
    }

    private static boolean samePayment(Income existing, B3IncomePreviewRow row) {
        if (existing.getPaymentDate() == null || existing.getType() == null ||
                existing.getAmountPerUnit() == null || existing.getQuantity() == null) return false;
        return existing.getPaymentDate().equals(row.paymentDate())
                && existing.getType().name().equals(row.type())
                && existing.getQuantity().compareTo(row.quantity()) == 0
                && (existing.getTotalAmount() == null
                        ? existing.getAmountPerUnit().multiply(existing.getQuantity()) : existing.getTotalAmount())
                   .subtract(row.creditedAmount()).abs().compareTo(new BigDecimal("0.01")) <= 0;
    }
}
