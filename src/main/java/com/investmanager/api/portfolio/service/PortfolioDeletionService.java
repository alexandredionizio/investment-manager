package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** IM-053: exclusão integral da carteira e de seus registros dependentes em uma transação. */
@Service
public class PortfolioDeletionService {
    private final JdbcTemplate jdbc;

    public PortfolioDeletionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void delete(Long portfolioId, Long userId) {
        // A trava serializa a exclusão com novas inserções que referenciem a carteira.
        boolean owned = Boolean.TRUE.equals(jdbc.query(
                "SELECT id FROM portfolios WHERE id = ? AND user_id = ? FOR UPDATE",
                (ResultSetExtractor<Boolean>) rs -> rs.next(), portfolioId, userId));
        if (!owned) {
            throw new PortfolioNotFoundException(portfolioId);
        }

        // B3: remover primeiro os estornos (que apontam para linhas e transações),
        // depois as linhas, os lotes e finalmente as transações.
        jdbc.update("""
                DELETE FROM b3_transaction_reversals
                WHERE transaction_id IN (SELECT id FROM transactions WHERE portfolio_id = ?)
                   OR batch_row_id IN (
                       SELECT r.id FROM b3_import_batch_rows r
                       JOIN b3_import_batches b ON b.id = r.batch_id
                       WHERE b.portfolio_id = ?)
                """, portfolioId, portfolioId);
        jdbc.update("""
                DELETE FROM b3_import_batch_rows
                WHERE batch_id IN (SELECT id FROM b3_import_batches WHERE portfolio_id = ?)
                """, portfolioId);
        jdbc.update("DELETE FROM b3_import_batches WHERE portfolio_id = ?", portfolioId);
        // V22: as tentativas rejeitadas não são lotes confirmados, mas também
        // referenciam a carteira. Suas linhas são removidas por ON DELETE CASCADE.
        jdbc.update("DELETE FROM b3_import_rejected_attempts WHERE portfolio_id = ?", portfolioId);
        jdbc.update("DELETE FROM valuation_contracts WHERE portfolio_id = ?", portfolioId);
        jdbc.update("DELETE FROM portfolio_asset_locks WHERE portfolio_id = ?", portfolioId);
        jdbc.update("DELETE FROM incomes WHERE portfolio_id = ?", portfolioId);
        jdbc.update("DELETE FROM transactions WHERE portfolio_id = ?", portfolioId);
        int deleted = jdbc.update("DELETE FROM portfolios WHERE id = ? AND user_id = ?", portfolioId, userId);
        if (deleted != 1) {
            throw new IllegalStateException("A carteira não pôde ser excluída integralmente.");
        }
    }
}
