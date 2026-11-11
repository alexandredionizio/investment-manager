package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class B3IncomeCommitIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private B3IncomeCommitService service;
    @Autowired private UserRepository users;
    @Autowired private PortfolioRepository portfolios;
    @Autowired private AssetRepository assets;
    @Autowired private IncomeRepository incomes;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private B3IncomePreviewService preview;
    @MockitoBean private B3IncomeReconciliationService reconcile;

    private final MockMultipartFile file = new MockMultipartFile("file", "movimentacao.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
    private Long user, portfolio, asset;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE transactions, incomes, portfolio_asset_locks, " +
                "b3_import_batch_rows, b3_import_batches, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        var owner = users.save(new User("IM054 Test", "im054-test@example.invalid", "test"));
        user = owner.getId();
        portfolio = portfolios.save(new Portfolio("Teste IM-054", "Somente Testcontainers", owner)).getId();
        var instrument = new Asset();
        instrument.setTicker("HGLG11");instrument.setName("FII de teste");
        instrument.setType(AssetType.FII);instrument.setExchange("B3");
        asset = assets.save(instrument).getId();
        var one = row(2, LocalDate.of(2026, 10, 15), "59.67");
        var two = row(3, LocalDate.of(2026, 10, 23), "60.20");
        when(preview.preview(any())).thenReturn(new B3IncomePreviewResponse("movimentacao.xlsx", 2, 2, 0, List.of(one, two)));
        when(reconcile.reconcile(any(), eq(portfolio), eq(user))).thenReturn(new B3IncomeReconciliationResponse(
                portfolio, 2, 2, 2, 0, List.of(
                new B3IncomeReconciliationResponse.Row(2, "HGLG11", asset, "READY", List.of()),
                new B3IncomeReconciliationResponse.Row(3, "HGLG11", asset, "READY", List.of()))));
    }

    private B3IncomePreviewRow row(int line, LocalDate payment, String amount) {
        return new B3IncomePreviewRow(line, payment, "Rendimento", "HGLG11 - FII", "XP", "FII_INCOME",
                new BigDecimal("51"), new BigDecimal("1.17"), new BigDecimal(amount), List.of(), List.of());
    }

    private B3IncomeCommitRequest request(int... lines) {
        var entries = java.util.Arrays.stream(lines)
                .mapToObj(i -> new B3IncomeCommitRequest.Entry(i)).toList();
        return new B3IncomeCommitRequest(true, entries);
    }

    @Test void importsExactCreditsAndRejectsSecondIdenticalImport() {
        var saved = service.commit(file, portfolio, user, request(2));
        assertEquals(1, saved.imported());
        assertEquals(0, saved.totalCredited().compareTo(new BigDecimal("59.67")));
        var records = incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(portfolio);
        assertEquals(1, records.size());
        assertEquals(0, records.getFirst().getTotalAmount().compareTo(new BigDecimal("59.67")));
        assertNull(records.getFirst().getBaseDate(), "Extrato B3 não informa data-base");
        assertThrows(RuntimeException.class, () -> service.commit(file, portfolio, user, request(2)));
        assertEquals(1, incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(portfolio).size());
    }

    @Test void rollsBackWhenSecondPaymentRepeatsFirstWithinSameBatch() {
        var duplicate = row(3, LocalDate.of(2026, 10, 15), "59.67");
        when(preview.preview(any())).thenReturn(new B3IncomePreviewResponse("movimentacao.xlsx", 2, 2, 0,
                List.of(row(2, LocalDate.of(2026, 10, 15), "59.67"), duplicate)));
        assertThrows(RuntimeException.class, () -> service.commit(file, portfolio, user, request(2, 3)));
        assertTrue(incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(portfolio).isEmpty(),
                "Lote inteiro deve sofrer rollback");
    }

    @Test void rejectsAnotherUsersPortfolioWithoutReadingFile() {
        assertThrows(RuntimeException.class, () -> service.commit(file, portfolio, user + 1, request(2)));
        verifyNoInteractions(preview, reconcile);
    }
}
