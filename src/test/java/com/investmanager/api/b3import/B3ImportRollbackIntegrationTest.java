package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.quote.service.QuoteService;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.service.TransactionService;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IM-043: testes reais de rollback em banco PostgreSQL efêmero.
 * Nunca executar contra o PostgreSQL local de desenvolvimento.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class B3ImportRollbackIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private B3ImportCommitService importService;
    @Autowired private UserRepository users;
    @Autowired private PortfolioRepository portfolios;
    @Autowired private AssetRepository assets;
    @Autowired private BrokerRepository brokers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.investmanager.api.position.service.PositionService positions;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private QuoteService quoteService;
    @MockitoBean private B3SpreadsheetPreviewService previews;
    @MockitoBean private B3MatchingService matching;
    @MockitoBean private B3ReconciliationService reconciliation;
    @MockitoSpyBean private TransactionService transactions;

    private final MultipartFile file = new MockMultipartFile("file", "im043-rollback.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

    private Long userId;
    private Long portfolioId;
    private Long brokerId;
    private List<Asset> registeredAssets;

    @BeforeEach
    void prepare() {
        // Este banco pertence EXCLUSIVAMENTE ao Testcontainers; nenhuma carteira do usuário é acessada.
        jdbc.execute("TRUNCATE transactions, incomes, portfolio_asset_locks, " +
                "b3_import_batch_rows, b3_import_batches, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        record Fixture(Long userId, Long portfolioId, Long brokerId, List<Asset> assets) {}
        Fixture fixture = new TransactionTemplate(transactionManager).execute(status -> {
            User user = users.save(new User("IM043 Test", "im043-rollback@test.invalid", "test"));
            Portfolio portfolio = portfolios.save(new Portfolio("IM043 - ROLLBACK", "Somente Testcontainers", user));
            Broker broker = new Broker();
            broker.setName("XP Test");
            brokers.save(broker);
            List<Asset> assetList = java.util.stream.Stream.of("PETR4", "ITUB4", "BBAS3")
                    .map(ticker -> {
                        Asset asset = new Asset();
                        asset.setTicker(ticker);
                        asset.setName(ticker);
                        asset.setType(AssetType.STOCK);
                        asset.setExchange("B3");
                        return assets.save(asset);
                    }).toList();
            return new Fixture(user.getId(), portfolio.getId(), broker.getId(), assetList);
        });
        assertNotNull(fixture);
        userId = fixture.userId();
        portfolioId = fixture.portfolioId();
        brokerId = fixture.brokerId();
        registeredAssets = fixture.assets();

        List<B3PreviewRow> sourceRows = List.of(
                previewRow(2, "PETR4", "100", "36", LocalDate.of(2026, 11, 2)),
                previewRow(3, "ITUB4", "80", "38", LocalDate.of(2026, 11, 3)),
                previewRow(4, "BBAS3", "60", "25", LocalDate.of(2026, 11, 4)));
        when(previews.preview(any(MultipartFile.class))).thenReturn(
                new B3PreviewResponse("im043-rollback.xlsx", 3, 3, 0, sourceRows));
        List<B3MatchResponse.Row> matches = sourceRows.stream().map(row -> {
            Asset asset = registeredAssets.stream().filter(a -> a.getTicker().equals(row.ticker())).findFirst().orElseThrow();
            return new B3MatchResponse.Row(row.excelRow(), row.ticker(), "XP", "MATCHED", asset.getId(),
                    "MATCHED", brokerId, List.of(), List.of());
        }).toList();
        when(matching.match(any(MultipartFile.class))).thenReturn(
                new B3MatchResponse("im043-rollback.xlsx", 3, 3, 0, 0, matches));
        List<B3ReconciliationResponse.Row> ready = sourceRows.stream().map(row -> {
            Asset asset = registeredAssets.stream().filter(a -> a.getTicker().equals(row.ticker())).findFirst().orElseThrow();
            return new B3ReconciliationResponse.Row(row.excelRow(), row.tradeDate(), row.ticker(), "XP",
                    "Compra", asset.getId(), brokerId, row.quantity(), row.unitPrice(), "READY", List.of());
        }).toList();
        when(reconciliation.reconcile(any(MultipartFile.class), eq(portfolioId), eq(userId)))
                .thenReturn(new B3ReconciliationResponse("im043-rollback.xlsx", portfolioId, 3, 3, 0, 0, ready));
        org.mockito.Mockito.reset(transactions);
    }

    private static B3PreviewRow previewRow(int line, String ticker, String quantity,
                                           String price, LocalDate date) {
        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal unit = new BigDecimal(price);
        return new B3PreviewRow(line, date, "Compra", "Mercado à Vista", "", "XP", ticker,
                qty, unit, qty.multiply(unit), List.of(), List.of());
    }

    @Test
    void shouldRollbackWholeBatchOnSecondTransactionAndAllowRetry() {
        assertEquals(0L, count("transactions"));
        assertEquals(0L, count("b3_import_batches"));
        assertEquals(0L, count("b3_import_batch_rows"));

        AtomicInteger attempts = new AtomicInteger();
        // O TransactionService usa @Transactional(MANDATORY). Como o spy e proxied pelo
        // Spring AOP, ate a chamada de configuracao do Mockito passa pelo interceptor.
        // Criamos uma transacao apenas para registrar o stub, sem gravar negociacoes.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            doAnswer(invocation -> {
                if (attempts.incrementAndGet() == 2) {
                    throw new IllegalStateException("FALHA_SIMULADA_IM043");
                }
                return invocation.callRealMethod();
            }).when(transactions).createConfirmed(any(TransactionRequest.class), eq(userId), anyString());
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> importService.commit(file, portfolioId, userId, true));
        assertEquals("FALHA_SIMULADA_IM043", failure.getMessage());
        assertEquals(2, attempts.get(), "A primeira compra deve ter sido processada antes da falha na segunda");
        assertEquals(0L, count("transactions"), "A primeira compra deve sofrer rollback");
        assertEquals(0L, count("b3_import_batches"));
        assertEquals(0L, count("b3_import_batch_rows"));
        assertEquals(0L, count("portfolio_asset_locks"), "Locks de coordenação também devem sofrer rollback");

        // Remove APENAS a falha artificial. Não modifica código de produção.
        org.mockito.Mockito.reset(transactions);
        B3ImportCommitResponse imported = importService.commit(file, portfolioId, userId, true);
        assertEquals("IMPORTED", imported.status());
        assertEquals(3, imported.importedRows());
        assertEquals(3L, count("transactions"));
        assertEquals(1L, count("b3_import_batches"));
        assertEquals(3L, count("b3_import_batch_rows"));
        // IM-045: a posição contábil deve refletir o commit inteiro, sem API de mercado.
        var accounting = positions.calculatePositions(portfolioId, userId);
        assertEquals(3, accounting.size());
        assertEquals(0, new BigDecimal("100").compareTo(accounting.stream()
                .filter(p -> p.assetTicker().equals("PETR4")).findFirst().orElseThrow().quantity()));
        assertEquals(0, new BigDecimal("80").compareTo(accounting.stream()
                .filter(p -> p.assetTicker().equals("ITUB4")).findFirst().orElseThrow().quantity()));
        assertEquals(0, new BigDecimal("60").compareTo(accounting.stream()
                .filter(p -> p.assetTicker().equals("BBAS3")).findFirst().orElseThrow().quantity()));
        // IM-045: o mesmo fluxo de posições alimenta o patrimônio exibido no Dashboard.
        // Preços são controlados neste teste: nenhuma requisição de mercado real.
        when(quoteService.getCurrentPrice("PETR4")).thenReturn(new BigDecimal("40"));
        when(quoteService.getCurrentPrice("ITUB4")).thenReturn(new BigDecimal("45"));
        when(quoteService.getCurrentPrice("BBAS3")).thenReturn(new BigDecimal("30"));
        var marketPositions = positions.calculateMarketPositions(portfolioId, userId);
        assertEquals(3, marketPositions.size());
        assertEquals(0, new BigDecimal("9400").compareTo(marketPositions.stream()
                .map(p -> p.currentValue()).reduce(BigDecimal.ZERO, BigDecimal::add)),
                "O valor de mercado deve ser a soma de todas as posições importadas");
        // Indisponibilidade do provedor não pode apagar posições contábeis persistidas.
        when(quoteService.getCurrentPrice("ITUB4"))
                .thenThrow(new com.investmanager.api.quote.exception.QuoteNotFoundException("ITUB4"));
        assertThrows(com.investmanager.api.quote.exception.QuoteNotFoundException.class,
                () -> positions.calculateMarketPositions(portfolioId, userId));
        assertEquals(3, positions.calculatePositions(portfolioId, userId).size());
        assertEquals(3L, count("transactions"));
        assertEquals(0L, count("incomes"), "Importar negociações não deve inventar proventos");
        assertEquals(3L, jdbc.queryForObject("""
                SELECT count(*) FROM transactions WHERE portfolio_id = ?
                  AND recorded_ticker IS NOT NULL
                """, Long.class, portfolioId));

        B3ImportCommitResponse repeated = importService.commit(file, portfolioId, userId, true);
        assertEquals("ALREADY_IMPORTED", repeated.status());
        assertEquals(0, repeated.importedRows());
        assertEquals(3L, count("transactions"));
        assertEquals(1L, count("b3_import_batches"));
        assertEquals(3L, count("b3_import_batch_rows"));
        assertEquals(3, positions.calculatePositions(portfolioId, userId).size());
        assertEquals(0L, count("incomes"), "Reimportação não deve gerar proventos");
    }

    @Test
    void confirmedHistoricalTickerPreservesTradeIdentityAndCurrentAssetPosition() {
        // IM-050: alias histórico CONFIRMADO, sem atualizar o ticker do ativo
        // nem reescrever negociações passadas. PostgreSQL descartável.
        Asset original = registeredAssets.stream()
                .filter(asset -> asset.getTicker().equals("PETR4")).findFirst().orElseThrow();
        jdbc.update("""
                INSERT INTO asset_identifiers(exchange, kind, value, asset_id, source, observed_name, effective_date)
                VALUES ('B3', 'TICKER', 'PETR3', ?, 'IM050_TEST', 'PETR3 histórico', ?)
                """, original.getId(), LocalDate.of(2026, 10, 1));
        B3PreviewRow historic = previewRow(2, "PETR3", "4", "29", LocalDate.of(2026, 11, 2));
        when(previews.preview(any(MultipartFile.class))).thenReturn(
                new B3PreviewResponse("im050-historico.xlsx", 1, 1, 0, List.of(historic)));
        when(matching.match(any(MultipartFile.class))).thenReturn(
                new B3MatchResponse("im050-historico.xlsx", 1, 1, 0, 0,
                        List.of(new B3MatchResponse.Row(2, "PETR3", "XP", "MATCHED", original.getId(),
                                "MATCHED", brokerId, List.of(), List.of()))));
        when(reconciliation.reconcile(any(MultipartFile.class), eq(portfolioId), eq(userId)))
                .thenReturn(new B3ReconciliationResponse("im050-historico.xlsx", portfolioId, 1, 1, 0, 0,
                        List.of(new B3ReconciliationResponse.Row(2, historic.tradeDate(), "PETR3", "XP",
                                "Compra", original.getId(), brokerId, historic.quantity(),
                                historic.unitPrice(), "READY", List.of()))));
        var imported = importService.commit(file, portfolioId, userId, true);
        assertEquals("IMPORTED", imported.status());
        assertEquals(1L, count("transactions"));
        assertEquals("PETR3", jdbc.queryForObject("""
                SELECT recorded_ticker FROM transactions WHERE portfolio_id = ?
                """, String.class, portfolioId));
        assertEquals("PETR4", assets.findById(original.getId()).orElseThrow().getTicker());
        var position = positions.calculatePositions(portfolioId, userId);
        assertEquals(1, position.size());
        assertEquals("PETR4", position.get(0).assetTicker());
        assertEquals(0, new BigDecimal("4").compareTo(position.get(0).quantity()));
        assertEquals(1L, count("b3_import_batch_rows"));
        assertEquals(0L, count("incomes"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE 1=1", Long.class);
    }
}
