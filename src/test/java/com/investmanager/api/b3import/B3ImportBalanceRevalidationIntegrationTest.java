package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.service.TransactionService;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * IM-043: alteracao de saldo entre previa e commit.
 * Banco Postgres EFEMERO do Testcontainers; nunca utiliza carteiras locais.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class B3ImportBalanceRevalidationIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private B3ImportCommitService importer;
    @Autowired private B3ReconciliationService reconciler; // REAL, sem mock
    @Autowired private TransactionService transactions; // REAL, sem mock
    @Autowired private UserRepository users;
    @Autowired private PortfolioRepository portfolios;
    @Autowired private AssetRepository assets;
    @Autowired private BrokerRepository brokers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private B3SpreadsheetPreviewService previews;
    @MockitoBean private B3MatchingService matching;

    private final MultipartFile file = new MockMultipartFile("file", "im043-saldo-alterado.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

    @Test
    void shouldRejectCommitWhenBalanceDropsAfterPreview() {
        // Fixture inteiramente isolada; todos os IDs sao gerados no banco do container.
        record Fixture(Long userId, Long portfolioId, Long assetId, Long brokerId) {}
        Fixture fixture = new TransactionTemplate(transactionManager).execute(status -> {
            User user = users.save(new User("IM043 Teste Saldo", "im043-saldo@test.invalid", "test"));
            Portfolio portfolio = portfolios.save(new Portfolio("IM043 SALDO ISOLADO", "Somente Testcontainers", user));
            Broker broker = new Broker();
            broker.setName("XP Teste IM043");
            broker = brokers.save(broker);
            Asset asset = new Asset();
            asset.setTicker("PETR4");
            asset.setName("PETR4");
            asset.setType(AssetType.STOCK);
            asset.setExchange("B3");
            asset = assets.save(asset);
            return new Fixture(user.getId(), portfolio.getId(), asset.getId(), broker.getId());
        });
        assertNotNull(fixture);
        Long userId = fixture.userId();
        Long portfolioId = fixture.portfolioId();
        Long assetId = fixture.assetId();
        Long brokerId = fixture.brokerId();

        // A carteira possui 10 PETR4, compradas ANTES da data do arquivo B3.
        transactions.create(new TransactionRequest(portfolioId, assetId, brokerId, TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("36"), LocalDate.of(2026, 10, 1)), userId);

        // Arquivo pretende vender 8 PETR4 em 08/11/2026.
        B3PreviewRow row = new B3PreviewRow(2, LocalDate.of(2026, 11, 8), "Venda",
                "Mercado à Vista", "", "XP", "PETR4", new BigDecimal("8"),
                new BigDecimal("37"), new BigDecimal("296"), List.of(), List.of());
        when(previews.preview(any(MultipartFile.class)))
                .thenReturn(new B3PreviewResponse("im043-saldo-alterado.xlsx", 1, 1, 0, List.of(row)));
        when(matching.match(any(MultipartFile.class)))
                .thenReturn(new B3MatchResponse("im043-saldo-alterado.xlsx", 1, 1, 0, 0,
                        List.of(new B3MatchResponse.Row(2, "PETR4", "XP", "MATCHED", assetId,
                                "MATCHED", brokerId, List.of(), List.of()))));

        // Simulacao anterior: venda de 8 seria permitida com saldo de 10.
        B3ReconciliationResponse before = reconciler.reconcile(file, portfolioId, userId);
        assertEquals(1, before.readyRows());
        assertEquals(0, before.reviewRows());
        assertEquals(0, before.blockedRows());
        assertEquals("READY", before.rows().get(0).status());

        // Outra operacao REAL (via servico do dominio) consome 6 unidades ANTES do commit.
        transactions.create(new TransactionRequest(portfolioId, assetId, brokerId, TransactionType.SELL,
                new BigDecimal("6"), new BigDecimal("38"), LocalDate.of(2026, 11, 5)), userId);
        assertEquals(2L, countTransactions(portfolioId));
        assertEquals(0L, countBatches(portfolioId));

        // A revalidacao real agora precisa BLOQUEAR 8 > 4, retornando HTTP 409.
        B3ReconciliationResponse changed = reconciler.reconcile(file, portfolioId, userId);
        assertEquals(0, changed.readyRows());
        assertEquals(1, changed.blockedRows());
        assertEquals("BLOCKED", changed.rows().get(0).status());
        ResponseStatusException rejected = assertThrows(ResponseStatusException.class,
                () -> importer.commit(file, portfolioId, userId, true));
        assertEquals(HttpStatus.CONFLICT.value(), rejected.getStatusCode().value());

        // Commit rejeitado nao cria terceiro lancamento nem lote, preserva saldo real de 4.
        assertEquals(2L, countTransactions(portfolioId));
        assertEquals(0L, countBatches(portfolioId));
        assertEquals(0L, countBatchRows());
        assertEquals(0, new BigDecimal("4").compareTo(balance(portfolioId, assetId)));
    }

    private long countTransactions(Long portfolioId) {
        return jdbc.queryForObject("SELECT count(*) FROM transactions WHERE portfolio_id = ?",
                Long.class, portfolioId);
    }
    private long countBatches(Long portfolioId) {
        return jdbc.queryForObject("SELECT count(*) FROM b3_import_batches WHERE portfolio_id = ?",
                Long.class, portfolioId);
    }
    private long countBatchRows() {
        return jdbc.queryForObject("SELECT count(*) FROM b3_import_batch_rows", Long.class);
    }
    private BigDecimal balance(Long portfolioId, Long assetId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'BUY' THEN quantity ELSE -quantity END), 0)
                FROM transactions WHERE portfolio_id = ? AND asset_id = ?
                """, BigDecimal.class, portfolioId, assetId);
    }
}
