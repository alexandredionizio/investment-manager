package com.investmanager.api.b3import;

import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** IM-054: somente linhas expressamente selecionadas e validadas; lote atômico. */
@Service
public class B3IncomeCommitService {
    private final B3IncomePreviewService previewService;
    private final B3IncomeReconciliationService reconciliationService;
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final IncomeRepository incomes;
    private final PortfolioAssetLockManager locks;

    public B3IncomeCommitService(B3IncomePreviewService previewService,
            B3IncomeReconciliationService reconciliationService, PortfolioRepository portfolios,
            AssetRepository assets, IncomeRepository incomes,
            PortfolioAssetLockManager locks) {
        this.previewService = previewService;
        this.reconciliationService = reconciliationService;
        this.portfolios = portfolios;
        this.assets = assets;
        this.incomes = incomes;
        this.locks = locks;
    }

    @Transactional
    public B3IncomeCommitResponse commit(MultipartFile file, Long portfolioId, Long userId,
                                         B3IncomeCommitRequest request) {
        if (request == null || !request.confirmed()) fail("Confirmação explícita obrigatória.");
        if (request.entries() == null || request.entries().isEmpty() || request.entries().size() > 1000)
            fail("Selecione entre 1 e 1000 proventos para importar.");
        var portfolio = portfolios.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carteira não encontrada."));
        var preview = previewService.preview(file);
        var reconciliation = reconciliationService.reconcile(file, portfolioId, userId);
        Map<Integer, B3IncomePreviewRow> byLine = preview.rows().stream()
                .collect(Collectors.toMap(B3IncomePreviewRow::excelRow, Function.identity()));
        Map<Integer, B3IncomeReconciliationResponse.Row> resolved = reconciliation.rows().stream()
                .collect(Collectors.toMap(B3IncomeReconciliationResponse.Row::excelRow, Function.identity()));
        Set<Integer> seenLines = new HashSet<>();
        Set<String> seenKeys = new HashSet<>();
        var created = new ArrayList<Long>();
        BigDecimal credited = BigDecimal.ZERO;
        for (var entry : request.entries()) {
            if (entry == null || !seenLines.add(entry.excelRow())) fail("Linha repetida na confirmação.");
            var row = byLine.get(entry.excelRow());
            var match = resolved.get(entry.excelRow());
            if (row == null || match == null || match.assetId() == null
                    || !"READY".equals(match.status()) || !row.errors().isEmpty())
                fail("Linha " + entry.excelRow() + " não está liberada para confirmação.");
            if (row.paymentDate() == null || row.quantity() == null || row.creditedAmount() == null)
                fail("Valores ausentes.");
            var asset = assets.findById(match.assetId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Ativo indisponível."));
            locks.acquire(portfolioId, asset.getId());
            var type = IncomeType.valueOf(row.type());
            var duplicates = incomes.findByPortfolioIdAndAssetIdOrderByBaseDateAscIdAsc(portfolioId, asset.getId());
            boolean existingPayment = duplicates.stream().anyMatch(previous ->
                    previous.getPaymentDate().equals(row.paymentDate()) && previous.getType() == type &&
                    previous.getQuantity().compareTo(row.quantity()) == 0 &&
                    previous.getTotalAmount() != null &&
                    previous.getTotalAmount().subtract(row.creditedAmount()).abs()
                            .compareTo(new BigDecimal("0.01")) <= 0);
            if (existingPayment) fail("Possível pagamento já registrado na linha " + entry.excelRow());
            var key = fingerprint(portfolioId, asset.getId(), row);
            if (!seenKeys.add(key)) fail("Pagamento repetido dentro do arquivo: linha " + entry.excelRow());
            var netUnit = row.creditedAmount().divide(row.quantity(), 8, RoundingMode.HALF_UP);
            if (netUnit.signum() <= 0) fail("Valor unitário líquido não representável.");
            var income = Income.fromB3Receipt(portfolio, asset, type, netUnit, row.quantity(),
                    row.paymentDate(), row.creditedAmount(), key);
            created.add(incomes.saveAndFlush(income).getId());
            credited = credited.add(row.creditedAmount());
        }
        return new B3IncomeCommitResponse(created.size(), credited, List.copyOf(created));
    }

    private static void fail(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private static String fingerprint(Long portfolioId, Long assetId, B3IncomePreviewRow row) {
        String institution = Normalizer.normalize(row.institution(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().toUpperCase(Locale.ROOT);
        String payload = portfolioId + "|" + assetId + "|" + row.type() + "|"
                + row.paymentDate() + "|" + row.quantity().stripTrailingZeros().toPlainString()
                + "|" + row.creditedAmount().stripTrailingZeros().toPlainString() + "|" + institution;
        try {
            var digester = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digester.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
