package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** IM-052: real persistence, locks, FK protection and history validation, isolated by Testcontainers. */
@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class TransactionDeletionIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired TransactionService transactions;
    @Autowired TransactionDeletionService deletion;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired AssetRepository assets;
    @Autowired BrokerRepository brokers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    record Fixture(Long userId, Long portfolioId, Long assetId, Long brokerId) {}
    private Fixture fixture() {
        return new TransactionTemplate(txManager).execute(status -> {
            String unique = java.util.UUID.randomUUID().toString();
            User user = users.save(new User("IM052", unique + "@test.invalid", "test"));
            Portfolio portfolio = portfolios.save(new Portfolio("IM052 isolada", "teste", user));
            Broker broker = new Broker(); broker.setName("Teste-" + unique); broker = brokers.save(broker);
            Asset asset = new Asset(); asset.setTicker("T" + unique.substring(0,7).toUpperCase());
            asset.setName("Ativo teste"); asset.setType(AssetType.STOCK); asset.setExchange("B3"); asset = assets.save(asset);
            return new Fixture(user.getId(), portfolio.getId(), asset.getId(), broker.getId());
        });
    }

    private Long trade(Fixture f, TransactionType type, int units, int day) {
        return transactions.create(new TransactionRequest(f.portfolioId(), f.assetId(), f.brokerId(),
                type, BigDecimal.valueOf(units), new BigDecimal("25"), LocalDate.of(2026, 11, day)), f.userId()).id();
    }

    private long count(Fixture f) {
        return jdbc.queryForObject("select count(*) from transactions where portfolio_id = ?", Long.class, f.portfolioId());
    }

    @Test void allowsDeletingManualPurchaseWithNoDependentOperations() {
        Fixture f = fixture();
        Long id = trade(f, TransactionType.BUY, 10, 2);
        deletion.deleteManual(id, f.userId());
        assertEquals(0L, count(f));
    }

    @Test void rejectsDeletionThatWouldMakeLaterSaleNegative() {
        Fixture f = fixture();
        Long buy = trade(f, TransactionType.BUY, 10, 2);
        trade(f, TransactionType.SELL, 8, 4);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> deletion.deleteManual(buy, f.userId()));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(2L, count(f));
    }

    @Test void refusesDeletionOfB3ImportedTransaction() {
        Fixture f = fixture();
        Long buy = trade(f, TransactionType.BUY, 10, 2);
        // An actual B3 batch FK-link, matching Flyway V15. Never delete imported lines.
        jdbc.update("""
            insert into b3_import_batches (portfolio_id, user_id, content_sha256, source_filename, row_count)
            values (?, ?, ?, ?, 1)
            """, f.portfolioId(), f.userId(), "a".repeat(64), "im052.xlsx");
        Long batchId = jdbc.queryForObject(
                "select id from b3_import_batches where portfolio_id = ?", Long.class, f.portfolioId());
        jdbc.update("insert into b3_import_batch_rows (batch_id, excel_row, transaction_id) values (?, 2, ?)", batchId, buy);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> deletion.deleteManual(buy, f.userId()));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(1L, count(f));
    }

    @Test void doesNotAllowAnotherUserToDeleteTransactions() {
        Fixture owner = fixture(); Fixture other = fixture();
        Long buy = trade(owner, TransactionType.BUY, 10, 2);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> deletion.deleteManual(buy, other.userId()));
        assertEquals(HttpStatus.NOT_FOUND.value(), error.getStatusCode().value());
        assertEquals(1L, count(owner));
    }
}
