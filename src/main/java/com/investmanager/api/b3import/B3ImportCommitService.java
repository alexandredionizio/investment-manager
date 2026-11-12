package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.service.TransactionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IM-042: importação integral, idempotente por conteúdo e carteira.
 * TODO: negociação B3 não tem ID de execução no Excel; nunca deduzimos duplicidade individual.
 * Operações marcadas REVIEW não podem ser importadas neste primeiro fluxo.
 */
@Service
public class B3ImportCommitService {
    private final B3SpreadsheetPreviewService previews;
    private final B3MatchingService matching;
    private final B3ReconciliationService reconciliation;
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final PortfolioAssetLockManager locks;
    private final TransactionCreationExecutor executor;
    private final TransactionService transactions;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final B3ImportRejectionAuditService rejectionAudit;

    public B3ImportCommitService(B3SpreadsheetPreviewService previews, B3MatchingService matching,
                                 B3ReconciliationService reconciliation, PortfolioRepository portfolios,
                                 AssetRepository assets, PortfolioAssetLockManager locks, TransactionCreationExecutor executor,
                                 TransactionService transactions, EntityManager entityManager, JdbcTemplate jdbc,
                                 B3ImportRejectionAuditService rejectionAudit) {
        this.previews = previews; this.matching = matching; this.reconciliation = reconciliation;
        this.portfolios = portfolios; this.assets = assets; this.locks = locks; this.executor = executor;
        this.transactions = transactions; this.entityManager = entityManager; this.jdbc = jdbc;
        this.rejectionAudit = rejectionAudit;
    }

    /** Histórico de lotes importados, sempre limitado à carteira do usuário autenticado. */
    public List<B3ImportBatchSummary> batches(Long portfolioId, Long userId) {
        if (portfolioId == null || userId == null || portfolios.findByIdAndUserId(portfolioId, userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carteira não encontrada.");
        return jdbc.query("""
                SELECT id, portfolio_id, source_filename, imported_at, row_count
                FROM b3_import_batches WHERE portfolio_id = ? AND user_id = ?
                ORDER BY imported_at DESC, id DESC
                """, (rs, index) -> new B3ImportBatchSummary(rs.getLong("id"), rs.getLong("portfolio_id"),
                rs.getString("source_filename"), rs.getObject("imported_at", OffsetDateTime.class),
                rs.getInt("row_count")), portfolioId, userId);
    }

    public B3ImportCommitResponse commit(MultipartFile file, Long portfolioId, Long userId, boolean confirmed) {
        if (!confirmed) throw badRequest("Confirme expressamente a importação antes da gravação.");
        if (portfolioId == null || userId == null) throw badRequest("Carteira inválida.");
        // MultipartFile é mantido imutável durante todas as leituras. Nenhuma chamada à Brapi dentro da transação.
        try {
            return executor.execute(() -> commitAtomically(file, portfolioId, userId));
        } catch (RuntimeException rejected) {
            // A tentativa confirmada foi recusada após rollback; auditoria em transação própria.
            // Não registrar tentativas sobre carteira alheia nem mascarar o erro original.
            try {
                if (portfolios.findByIdAndUserId(portfolioId, userId).isPresent()) {
                    rejectionAudit.record(file, portfolioId, userId,
                            rejected instanceof ResponseStatusException status
                                    ? status.getReason() == null ? "Importação recusada." : status.getReason()
                                    : rejected instanceof IllegalArgumentException && rejected.getMessage() != null
                                        ? rejected.getMessage() : "Tentativa não concluída; verifique regras de negócio e disponibilidade.");
                }
            } catch (RuntimeException auditFailure) {
                // Falha da auditoria não pode mascarar a recusa original, mas fica rastreável no log.
                rejected.addSuppressed(auditFailure);
            }
            throw rejected;
        }
    }

    private B3ImportCommitResponse commitAtomically(MultipartFile file, Long portfolioId, Long userId) {
        // A linha da carteira serializa importações concorrentes na MESMA carteira.
        Portfolio portfolio = portfolios.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carteira não encontrada."));
        entityManager.lock(portfolio, LockModeType.PESSIMISTIC_WRITE);

        B3PreviewResponse preview = previews.preview(file);
        if (preview.totalRows() == 0 || preview.invalidRows() > 0 || preview.totalRows() > B3SpreadsheetPreviewService.MAX_ROWS)
            throw conflict("A planilha tem linhas inválidas ou está vazia.");
        B3MatchResponse matches = matching.match(file);
        if (matches.totalRows() != preview.totalRows() || matches.matchedRows() != preview.totalRows())
            throw conflict("Todos os ativos e corretoras precisam estar identificados.");
        Map<Integer, B3MatchResponse.Row> matched = matches.rows().stream()
                .collect(Collectors.toMap(B3MatchResponse.Row::excelRow, Function.identity(), (a,b) -> {
                    throw conflict("Linha repetida na correspondência.");
                }));
        if (matched.size() != preview.rows().size()) throw conflict("A prévia e o cadastro divergem.");

        // Hash sem nome do arquivo, metadados de Excel, nem diferenças numéricas 50.0/50.00.
        // Inclui dados de negócio, IDs resolvidos e ordem das linhas para reimportações idênticas.
        String hash = fingerprint(preview, matched);
        List<B3ImportCommitResponse> previous = jdbc.query("""
                SELECT id, row_count FROM b3_import_batches
                WHERE portfolio_id = ? AND content_sha256 = ?
                """, (rs, rowNum) -> new B3ImportCommitResponse(rs.getLong("id"), portfolioId,
                "ALREADY_IMPORTED", rs.getInt("row_count"), List.of()), portfolioId, hash);
        if (!previous.isEmpty()) {
            var old = previous.get(0);
            var rows = jdbc.query("""
                    SELECT excel_row, transaction_id FROM b3_import_batch_rows
                    WHERE batch_id = ? ORDER BY excel_row
                    """, (rs, rowNum) -> new B3ImportCommitResponse.ImportedRow(
                    rs.getInt("excel_row"), rs.getLong("transaction_id")), old.batchId());
            return new B3ImportCommitResponse(old.batchId(), portfolioId, "ALREADY_IMPORTED", 0, rows);
        }

        // A negociação exportada pela B3 não contém um identificador único de execução.
        // Um arquivo parcialmente sobreposto não é seguro para importação automática:
        // transações iguais podem ser reenvios OU negociações legítimas distintas.
        // Após o teste de hash do lote completo, bloquear a ambiguidade e exigir revisão
        // humana, sempre antes da gravação financeira. A trava da carteira já foi obtida.
        for (B3PreviewRow row : preview.rows()) {
            var link = matched.get(row.excelRow());
            Long overlap = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM b3_import_batch_rows imported
                    JOIN b3_import_batches batch ON batch.id = imported.batch_id
                    JOIN transactions trade ON trade.id = imported.transaction_id
                    WHERE batch.portfolio_id = ? AND batch.user_id = ?
                      AND trade.asset_id = ? AND trade.broker_id = ?
                      AND trade.type = ? AND trade.transaction_date = ?
                      AND trade.quantity = ? AND trade.unit_price = ?
                    """, Long.class, portfolioId, userId, link.assetId(), link.brokerId(),
                    "Compra".equalsIgnoreCase(row.movementType()) ? "BUY" : "SELL",
                    row.tradeDate(), row.quantity(), row.unitPrice());
            if (overlap != null && overlap > 0) {
                throw conflict("Linha " + row.excelRow() +
                        " corresponde a negociação já presente em outro lote B3. " +
                        "Sem identificador de execução, revise manualmente para evitar duplicidade.");
            }
        }

        // Contrato de seguranca: nao recriar silenciosamente uma operacao B3 estornada,
        // mesmo que o arquivo tenha sido alterado e seu hash nao coincida com o lote original.
        for (B3PreviewRow row : preview.rows()) {
            var link = matched.get(row.excelRow());
            long matchingReversed = jdbc.queryForObject("""
                SELECT count(*) FROM b3_transaction_reversals v
                JOIN transactions t ON t.id = v.transaction_id
                WHERE t.portfolio_id = ? AND t.asset_id = ? AND t.broker_id = ?
                  AND t.type = ? AND t.transaction_date = ?
                  AND t.quantity = ? AND t.unit_price = ?
                """, Long.class, portfolioId, link.assetId(), link.brokerId(),
                    "Compra".equalsIgnoreCase(row.movementType()) ? "BUY" : "SELL",
                    row.tradeDate(), row.quantity(), row.unitPrice());
            if (matchingReversed > 0) throw conflict(
                    "Linha " + row.excelRow() + " coincide com operacao B3 estornada. Exige revisao manual; nao sera reimportada.");
        }

        // Nunca permitir que um ticker da B3 seja registrado sobre um cadastro de outra classe/bolsa.
        for (B3PreviewRow row : preview.rows()) {
            B3MatchResponse.Row link = matched.get(row.excelRow());
            Asset asset = assets.findById(link.assetId())
                    .orElseThrow(() -> conflict("Ativo da linha " + row.excelRow() + " não existe mais."));
            if (!Set.of(AssetType.STOCK, AssetType.FII, AssetType.ETF).contains(asset.getType())
                    || (asset.getExchange() != null && !asset.getExchange().isBlank()
                        && !"B3".equalsIgnoreCase(asset.getExchange())))
                throw conflict("Ativo da linha " + row.excelRow() + " não está elegível como B3.");
            if (!row.ticker().equalsIgnoreCase(asset.getTicker())) {
                // Ticker histórico: a prévia deve continuar resolvendo uma identidade única
                // no instante da gravação. Não aceitar colisão com ticker atual de outro ativo.
                String ticker = row.ticker().strip().toUpperCase(Locale.ROOT);
                Set<Long> candidates = new HashSet<>();
                assets.findByNormalizedTicker(ticker).forEach(a -> candidates.add(a.getId()));
                assets.findByConfirmedB3Ticker(ticker).forEach(a -> candidates.add(a.getId()));
                if (candidates.size() != 1 || !candidates.contains(link.assetId()))
                    throw conflict("Ativo da linha " + row.excelRow()
                            + " possui identidade histórica não confirmada ou ambígua; revise a associação B3.");
            }
        }

        // Bloqueio ordenado POR ATIVO: coordenação idêntica ao registro manual, evita deadlock entre lotes.
        matched.values().stream().map(B3MatchResponse.Row::assetId).distinct().sorted()
                .forEach(assetId -> locks.acquire(portfolioId, assetId));

        // Só após adquirir TODOS os locks fazemos a revalidação contra o histórico mais recente.
        B3ReconciliationResponse validation = reconciliation.reconcile(file, portfolioId, userId);
        if (validation.totalRows() != preview.totalRows() || validation.readyRows() != preview.totalRows()
                || validation.reviewRows() != 0 || validation.blockedRows() != 0)
            throw conflict("Conciliação alterada ou pendente. Refaça a conferência B3; nenhuma operação foi gravada.");

        // Conservador: não importar derivativos, mercados não à vista, prazos ou divergências de valor.
        for (B3PreviewRow row : preview.rows()) {
            if (!row.warnings().isEmpty() || !("MERCADO A VISTA".equals(normalizeMarket(row.market())) || "VISTA".equals(normalizeMarket(row.market())))
                || (row.expiry() != null && !row.expiry().isBlank() && !"-".equals(row.expiry().strip()))
                || row.tradeDate() == null || row.tradeDate().isAfter(LocalDate.now())
                || row.quantity() == null || row.unitPrice() == null || row.totalValue() == null
                || row.quantity().stripTrailingZeros().scale() > 8 || row.unitPrice().stripTrailingZeros().scale() > 8
                || row.quantity().multiply(row.unitPrice()).compareTo(row.totalValue()) != 0)
                throw conflict("Linha " + row.excelRow() + " exige revisão de mercado, valores ou data.");
        }

        Long batchId = jdbc.queryForObject("""
                INSERT INTO b3_import_batches(portfolio_id, user_id, content_sha256, source_filename, row_count)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, portfolioId, userId, hash, preview.fileName(), preview.totalRows());
        if (batchId == null) throw new IllegalStateException("O lote não recebeu identificador.");

        List<B3ImportCommitResponse.ImportedRow> rows = new ArrayList<>();
        List<B3PreviewRow> sorted = preview.rows().stream()
                .sorted(Comparator.comparing(B3PreviewRow::tradeDate).thenComparingInt(B3PreviewRow::excelRow))
                .toList();
        for (B3PreviewRow row : sorted) {
            var link = matched.get(row.excelRow());
            TransactionType type = "Compra".equalsIgnoreCase(row.movementType()) ? TransactionType.BUY : TransactionType.SELL;
            var request = new TransactionRequest(portfolioId, link.assetId(), link.brokerId(), type,
                    row.quantity(), row.unitPrice(), row.tradeDate());
            // Executa validações atuais de venda, histórico, proventos e bloqueio; NÃO chama executor individual.
            TransactionResponse saved = transactions.createConfirmed(request, userId, row.ticker());
            jdbc.update("""
                    INSERT INTO b3_import_batch_rows(batch_id, excel_row, transaction_id) VALUES (?, ?, ?)
                    """, batchId, row.excelRow(), saved.id());
            rows.add(new B3ImportCommitResponse.ImportedRow(row.excelRow(), saved.id()));
        }
        rows.sort(Comparator.comparingInt(B3ImportCommitResponse.ImportedRow::excelRow));
        return new B3ImportCommitResponse(batchId, portfolioId, "IMPORTED", rows.size(), List.copyOf(rows));
    }

    static String fingerprint(B3PreviewResponse preview, Map<Integer, B3MatchResponse.Row> matched) {
        StringBuilder text = new StringBuilder();
        for (B3PreviewRow row : preview.rows()) {
            B3MatchResponse.Row link = matched.get(row.excelRow());
            text.append(row.excelRow()).append('|').append(row.tradeDate()).append('|')
                    .append(row.movementType()).append('|').append(row.market()).append('|')
                    .append(row.expiry()).append('|').append(row.institution()).append('|')
                    .append(row.ticker()).append('|').append(link.assetId()).append('|')
                    .append(link.brokerId()).append('|').append(decimal(row.quantity())).append('|')
                    .append(decimal(row.unitPrice())).append('|').append(decimal(row.totalValue())).append('\n');
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String decimal(BigDecimal n) { return n == null ? "" : n.stripTrailingZeros().toPlainString(); }
    private static String normalizeMarket(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
