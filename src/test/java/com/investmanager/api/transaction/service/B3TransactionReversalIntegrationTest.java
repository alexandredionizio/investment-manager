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
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class B3TransactionReversalIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired B3TransactionReversalService reversal;
    @Autowired TransactionService transactionService;
    @Autowired TransactionRepository transactions;
    @Autowired com.investmanager.api.position.service.PositionService positions;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired AssetRepository assets;
    @Autowired BrokerRepository brokers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    record Fixture(Long userId, Long portfolioId, Long assetId, Long brokerId) {}
    private Fixture fixture() {
        return new TransactionTemplate(txManager).execute(status -> {
            String unique = UUID.randomUUID().toString();
            User user = users.save(new User("IM052B3", unique + "@test.invalid", "test"));
            Portfolio portfolio = portfolios.save(new Portfolio("IM052 B3 isolada", "teste", user));
            Broker broker = new Broker(); broker.setName("IM052B3-" + unique); broker = brokers.save(broker);
            Asset asset = new Asset(); asset.setTicker("B" + unique.substring(0,7).toUpperCase());
            asset.setName("Teste B3"); asset.setType(AssetType.STOCK); asset.setExchange("B3"); asset = assets.save(asset);
            return new Fixture(user.getId(), portfolio.getId(), asset.getId(), broker.getId());
        });
    }
    private Long trade(Fixture f, TransactionType type, int units, int day) {
        return transactionService.create(new TransactionRequest(f.portfolioId(), f.assetId(), f.brokerId(),
                type, BigDecimal.valueOf(units), new BigDecimal("25"), LocalDate.of(2026, 11, day)), f.userId()).id();
    }
    private long batchLink(Fixture f, Long transactionId) {
        String hash = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
            INSERT INTO b3_import_batches(portfolio_id,user_id,content_sha256,source_filename,row_count)
            VALUES (?,?,?,?,1)
            """, f.portfolioId(),f.userId(), (hash + hash), "reversal.xlsx");
        Long batchId = jdbc.queryForObject("SELECT id FROM b3_import_batches WHERE portfolio_id=? ORDER BY id DESC LIMIT 1",
                Long.class, f.portfolioId());
        jdbc.update("INSERT INTO b3_import_batch_rows(batch_id,excel_row,transaction_id) VALUES (?,2,?)",batchId,transactionId);
        return batchId;
    }
    private long count(String table, Fixture f) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE portfolio_id=?", Long.class, f.portfolioId());
    }
    private static final String REASON = "Operacao duplicada identificada na conciliacao";

    @Test void reversesImportedTradeWithoutDeletingSourceRowAndKeepsAudit() {
        Fixture f = fixture();
        Long transactionId = trade(f, TransactionType.BUY, 10, 2);
        long batchId = batchLink(f, transactionId);
        assertEquals(1, reversal.importedTransactionIds(f.portfolioId(),f.userId()).size());
        var result = reversal.reverse(transactionId, f.userId(),REASON,true);
        assertEquals(transactionId,result.transactionId());
        assertEquals(batchId,result.batchId());
        assertEquals(0, transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(f.portfolioId(),f.assetId()).size());
        assertTrue(positions.calculatePositions(f.portfolioId(),f.userId()).isEmpty());
        assertEquals(1L,count("transactions",f));
        assertEquals(1L,count("b3_import_batches",f));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM b3_import_batch_rows WHERE transaction_id=?",Long.class,transactionId));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM b3_transaction_reversals WHERE transaction_id=?",Long.class,transactionId));
        assertTrue(reversal.importedTransactionIds(f.portfolioId(),f.userId()).isEmpty());
        assertEquals(REASON,reversal.reversals(f.portfolioId(),f.userId()).get(0).reason());
        ResponseStatusException repeated = assertThrows(ResponseStatusException.class,
                () -> reversal.reverse(transactionId,f.userId(),REASON,true));
        assertTrue(repeated.getStatusCode().value() == 404 || repeated.getStatusCode().value() == 409);
    }

    @Test void rejectsReversalThatWouldInvalidateLaterSale() {
        Fixture f = fixture();
        Long buy = trade(f,TransactionType.BUY,10,2);
        trade(f,TransactionType.SELL,8,4);
        batchLink(f,buy);
        var ex = assertThrows(ResponseStatusException.class, () -> reversal.reverse(buy,f.userId(),REASON,true));
        assertEquals(409,ex.getStatusCode().value());
        assertEquals(2,transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(f.portfolioId(),f.assetId()).size());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM b3_transaction_reversals WHERE transaction_id=?",Long.class,buy));
    }

    @Test void rejectsManualTransactionMissingConfirmationAndBadReason() {
        Fixture f = fixture();
        Long manual = trade(f,TransactionType.BUY,10,2);
        assertEquals(400,assertThrows(ResponseStatusException.class,
                () -> reversal.reverse(manual,f.userId(),REASON,false)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,
                () -> reversal.reverse(manual,f.userId(),"curto",true)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,
                () -> reversal.reverse(manual,f.userId(),REASON,true)).getStatusCode().value());
        assertEquals(1,transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(f.portfolioId(),f.assetId()).size());
    }

    @Test void refusesCrossUserReversalAndAuditVisibility() {
        Fixture f = fixture(); Fixture other = fixture();
        Long id = trade(f,TransactionType.BUY,10,2);
        batchLink(f,id);
        assertEquals(404,assertThrows(ResponseStatusException.class,
                () -> reversal.reverse(id,other.userId(),REASON,true)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,
                () -> reversal.reversals(f.portfolioId(),other.userId())).getStatusCode().value());
        assertEquals(1, reversal.importedTransactionIds(f.portfolioId(),f.userId()).size());
    }
}
