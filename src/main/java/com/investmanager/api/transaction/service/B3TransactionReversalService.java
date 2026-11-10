package com.investmanager.api.transaction.service;

import com.investmanager.api.income.Income;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/** IM-052: estorno logico auditavel de UMA linha importada da B3. */
@Service
public class B3TransactionReversalService {
    private final JdbcTemplate jdbc;
    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final PortfolioRepository portfolios;
    private final PortfolioAssetLockManager locks;
    private final TransactionCreationExecutor executor;

    public B3TransactionReversalService(JdbcTemplate jdbc, TransactionRepository transactions,
            IncomeRepository incomes, PortfolioRepository portfolios,
            PortfolioAssetLockManager locks, TransactionCreationExecutor executor) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.incomes = incomes;
        this.portfolios = portfolios;
        this.locks = locks;
        this.executor = executor;
    }

    public record Reversal(Long transactionId, Long batchId, int excelRow, String reason,
                           OffsetDateTime reversedAt) {}

    public List<Long> importedTransactionIds(Long portfolioId, Long userId) {
        checkOwner(portfolioId, userId);
        return jdbc.queryForList("""
            SELECT r.transaction_id FROM b3_import_batch_rows r
            JOIN b3_import_batches b ON b.id = r.batch_id
            JOIN transactions t ON t.id = r.transaction_id
            WHERE b.portfolio_id = ? AND b.user_id = ? AND t.reversed_at IS NULL
            ORDER BY r.transaction_id
            """, Long.class, portfolioId, userId);
    }

    public List<Reversal> reversals(Long portfolioId, Long userId) {
        checkOwner(portfolioId, userId);
        return jdbc.query("""
            SELECT v.transaction_id, b.id AS batch_id, r.excel_row, v.reason, v.reversed_at
            FROM b3_transaction_reversals v
            JOIN b3_import_batch_rows r ON r.id = v.batch_row_id
            JOIN b3_import_batches b ON b.id = r.batch_id
            WHERE b.portfolio_id = ? AND b.user_id = ?
            ORDER BY v.reversed_at DESC, v.id DESC
            """, (rs,i) -> new Reversal(rs.getLong("transaction_id"),
                rs.getLong("batch_id"),rs.getInt("excel_row"),rs.getString("reason"),
                rs.getObject("reversed_at", OffsetDateTime.class)), portfolioId, userId);
    }

    public Reversal reverse(Long transactionId, Long userId, String reason, boolean confirmed) {
        if (!confirmed) throw error(HttpStatus.BAD_REQUEST, "A confirmacao explicita e obrigatoria.");
        if (reason == null || reason.strip().length() < 10 || reason.strip().length() > 500)
            throw error(HttpStatus.BAD_REQUEST, "Informe motivo entre 10 e 500 caracteres.");
        if (transactionId == null || userId == null) throw error(HttpStatus.BAD_REQUEST, "Dados invalidos.");
        final String explanation = reason.strip();
        return executor.execute(() -> reverseAtomically(transactionId, userId, explanation));
    }

    private Reversal reverseAtomically(Long transactionId, Long userId, String reason) {
        // Consulta JPA aplica automaticamente o filtro de estornos anteriores.
        Transaction original = transactions.findByIdAndPortfolioUserId(transactionId, userId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Transacao ativa nao encontrada."));
        Long portfolioId = original.getPortfolio().getId();
        Long assetId = original.getAsset().getId();
        locks.acquire(portfolioId, assetId);
        Transaction target = transactions.findByIdAndPortfolioUserId(transactionId, userId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "Transacao ja estornada ou inexistente."));

        List<SourceRow> rows = jdbc.query("""
            SELECT r.id AS row_id, b.id AS batch_id, r.excel_row
            FROM b3_import_batch_rows r JOIN b3_import_batches b ON b.id = r.batch_id
            WHERE r.transaction_id = ? AND b.portfolio_id = ? AND b.user_id = ?
            """, (rs,i) -> new SourceRow(rs.getLong("row_id"), rs.getLong("batch_id"),
                    rs.getInt("excel_row")), transactionId, portfolioId, userId);
        if (rows.size() != 1)
            throw error(HttpStatus.CONFLICT, "Somente transacoes vinculadas a um lote B3 podem ser estornadas.");

        List<Transaction> history = transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(portfolioId, assetId);
        List<Income> incomeHistory = incomes.findByPortfolioIdAndAssetIdOrderByBaseDateAscIdAsc(portfolioId, assetId);
        TransactionDeletionService.validateRemainingHistory(history, incomeHistory, target.getId());

        // UPDATE e INSERT na MESMA transacao; nenhum lote ou vinculo B3 e apagado.
        int changed = jdbc.update("UPDATE transactions SET reversed_at = CURRENT_TIMESTAMP WHERE id = ? AND reversed_at IS NULL", transactionId);
        if (changed != 1) throw error(HttpStatus.CONFLICT, "Transacao ja estornada.");
        jdbc.update("""
            INSERT INTO b3_transaction_reversals (transaction_id, batch_row_id, user_id, reason)
            VALUES (?, ?, ?, ?)
            """, transactionId, rows.get(0).rowId(), userId, reason);
        return jdbc.queryForObject("""
            SELECT v.transaction_id, b.id AS batch_id, r.excel_row, v.reason, v.reversed_at
            FROM b3_transaction_reversals v
            JOIN b3_import_batch_rows r ON r.id = v.batch_row_id
            JOIN b3_import_batches b ON b.id = r.batch_id
            WHERE v.transaction_id = ?
            """, (rs,i) -> new Reversal(rs.getLong("transaction_id"), rs.getLong("batch_id"),
                rs.getInt("excel_row"), rs.getString("reason"),
                rs.getObject("reversed_at", OffsetDateTime.class)), transactionId);
    }

    private record SourceRow(Long rowId, Long batchId, int excelRow) {}

    private void checkOwner(Long portfolioId, Long userId) {
        if (portfolioId == null || userId == null || portfolios.findByIdAndUserId(portfolioId, userId).isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Carteira nao encontrada.");
    }
    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
