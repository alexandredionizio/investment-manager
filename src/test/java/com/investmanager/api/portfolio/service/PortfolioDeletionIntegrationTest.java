package com.investmanager.api.portfolio.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IM-053: integridade referencial e rollback em PostgreSQL descartavel.
 * Este teste NUNCA utiliza o banco local do Investment Manager.
 */
@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class PortfolioDeletionIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired PortfolioDeletionService deletion;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired AssetRepository assets;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    record Fixture(Long userId, Long portfolioId, Long assetId, Long transactionId,
                   Long batchId, Long rowId, Long reversalId) {}

    private Fixture fixture(boolean withB3Reversal) {
        String unique = UUID.randomUUID().toString();
        record Base(Long userId, Long portfolioId, Long assetId) {}
        Base base = new TransactionTemplate(transactionManager).execute(status -> {
            User user = users.save(new User("IM053", unique + "@test.invalid", "test"));
            Portfolio portfolio = portfolios.save(new Portfolio("IM053 descartavel", "Testcontainers", user));
            Asset asset = new Asset();
            asset.setTicker("T" + unique.substring(0, 7).toUpperCase());
            asset.setName("Ativo IM053");
            asset.setType(AssetType.STOCK);
            asset.setExchange("B3");
            asset = assets.save(asset);
            return new Base(user.getId(), portfolio.getId(), asset.getId());
        });
        assertNotNull(base);
        Long pid = base.portfolioId();
        Long aid = base.assetId();
        Long tid = jdbc.queryForObject("""
                INSERT INTO transactions (portfolio_id, asset_id, type, quantity, unit_price, transaction_date)
                VALUES (?, ?, 'BUY', 10, 25, DATE '2026-11-02') RETURNING id
                """, Long.class, pid, aid);
        jdbc.update("""
                INSERT INTO incomes (portfolio_id, asset_id, type, amount_per_unit, quantity, base_date, payment_date)
                VALUES (?, ?, 'DIVIDEND', 1, 10, DATE '2026-11-03', DATE '2026-11-04')
                """, pid, aid);
        jdbc.update("INSERT INTO portfolio_asset_locks (portfolio_id, asset_id) VALUES (?, ?)", pid, aid);
        jdbc.update("INSERT INTO valuation_contracts (portfolio_id, asset_id, terms_json) VALUES (?, ?, '{}')", pid, aid);

        Long bid = null;
        Long rid = null;
        Long reversal = null;
        if (withB3Reversal) {
            bid = jdbc.queryForObject("""
                    INSERT INTO b3_import_batches (portfolio_id, user_id, content_sha256, source_filename, row_count)
                    VALUES (?, ?, ?, 'im053-test.xlsx', 1) RETURNING id
                    """, Long.class, pid, base.userId(), "b".repeat(64));
            rid = jdbc.queryForObject("""
                    INSERT INTO b3_import_batch_rows (batch_id, excel_row, transaction_id)
                    VALUES (?, 2, ?) RETURNING id
                    """, Long.class, bid, tid);
            jdbc.update("UPDATE transactions SET reversed_at = CURRENT_TIMESTAMP WHERE id = ?", tid);
            reversal = jdbc.queryForObject("""
                    INSERT INTO b3_transaction_reversals (transaction_id, batch_row_id, user_id, reason)
                    VALUES (?, ?, ?, 'Estorno ficticio IM053') RETURNING id
                    """, Long.class, tid, rid, base.userId());
        }
        return new Fixture(base.userId(), pid, aid, tid, bid, rid, reversal);
    }

    private long count(String table, String idColumn, Long id) {
        // Tabela e coluna sao exclusivamente constantes definidas pelo proprio teste.
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = ?",
                Long.class, id);
    }

    @Test
    void deletesB3AuditAndAllDependentsButPreservesAnotherPortfolio() {
        Fixture target = fixture(true);
        Fixture other = fixture(true);

        deletion.delete(target.portfolioId(), target.userId());

        assertEquals(0, count("portfolios", "id", target.portfolioId()));
        assertEquals(0, count("transactions", "id", target.transactionId()));
        assertEquals(0, count("incomes", "portfolio_id", target.portfolioId()));
        assertEquals(0, count("valuation_contracts", "portfolio_id", target.portfolioId()));
        assertEquals(0, count("portfolio_asset_locks", "portfolio_id", target.portfolioId()));
        assertEquals(0, count("b3_import_batches", "id", target.batchId()));
        assertEquals(0, count("b3_import_batch_rows", "id", target.rowId()));
        assertEquals(0, count("b3_transaction_reversals", "id", target.reversalId()));

        assertEquals(1, count("portfolios", "id", other.portfolioId()));
        assertEquals(1, count("transactions", "id", other.transactionId()));
        assertEquals(1, count("incomes", "portfolio_id", other.portfolioId()));
        assertEquals(1, count("b3_import_batches", "id", other.batchId()));
        assertEquals(1, count("b3_import_batch_rows", "id", other.rowId()));
        assertEquals(1, count("b3_transaction_reversals", "id", other.reversalId()));
    }

    @Test
    void doesNotDeleteAnotherUsersPortfolio() {
        Fixture owner = fixture(true);
        Fixture intruder = fixture(false);

        assertThrows(PortfolioNotFoundException.class,
                () -> deletion.delete(owner.portfolioId(), intruder.userId()));

        assertEquals(1, count("portfolios", "id", owner.portfolioId()));
        assertEquals(1, count("transactions", "id", owner.transactionId()));
        assertEquals(1, count("b3_transaction_reversals", "id", owner.reversalId()));
    }

    @Test
    void rollsBackDeletedChildrenWhenForeignKeyBlocksPortfolioDeletion() {
        Fixture target = fixture(true);
        // Restricao artificial somente neste PostgreSQL de teste: falha na ultima etapa
        // da exclusao, apos os DELETEs das tabelas filhas.
        String guardTable = "im053_guard_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            jdbc.execute("CREATE TABLE " + guardTable + " (portfolio_id BIGINT NOT NULL REFERENCES portfolios(id))");
            jdbc.update("INSERT INTO " + guardTable + " (portfolio_id) VALUES (?)", target.portfolioId());

            assertThrows(RuntimeException.class, () -> deletion.delete(target.portfolioId(), target.userId()));

            // O rollback deve recuperar TODOS os registros que os primeiros DELETEs removeram.
            assertEquals(1, count("portfolios", "id", target.portfolioId()));
            assertEquals(1, count("transactions", "id", target.transactionId()));
            assertEquals(1, count("incomes", "portfolio_id", target.portfolioId()));
            assertEquals(1, count("valuation_contracts", "portfolio_id", target.portfolioId()));
            assertEquals(1, count("portfolio_asset_locks", "portfolio_id", target.portfolioId()));
            assertEquals(1, count("b3_import_batches", "id", target.batchId()));
            assertEquals(1, count("b3_import_batch_rows", "id", target.rowId()));
            assertEquals(1, count("b3_transaction_reversals", "id", target.reversalId()));
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS " + guardTable);
        }
    }
}
