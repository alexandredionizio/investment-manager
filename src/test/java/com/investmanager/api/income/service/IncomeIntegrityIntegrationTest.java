package com.investmanager.api.income.service;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.discovery.BrapiCatalogClient;
import com.investmanager.api.asset.discovery.InstrumentEvidence;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.income.*;
import com.investmanager.api.income.controller.IncomeController;
import com.investmanager.api.income.dto.*;
import com.investmanager.api.income.exception.*;
import com.investmanager.api.income.mapper.IncomeMapper;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.shared.exception.GlobalExceptionHandler;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.controller.TransactionController;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.mapper.TransactionMapper;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.transaction.service.TransactionService;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.investmanager.api.transaction.TransactionType.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "investment.transactions.concurrency.lock-timeout-ms=5000"})
@Testcontainers
@Import(IncomeIntegrityIntegrationTest.FixedDate.class)
class IncomeIntegrityIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @TestConfiguration static class FixedDate {
        @Bean @Primary IncomePaymentPolicy fixedIncomePaymentPolicy() {
            return new IncomePaymentPolicy(Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneId.of("America/Sao_Paulo")));
        }
    }
    @Autowired IncomeService incomes;
    @Autowired TransactionService transactions;
    @Autowired IncomeRepository incomeRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired AssetRepository assets;
    @Autowired BrokerRepository brokers;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired IncomeController incomeController;
    @Autowired TransactionController transactionController;
    @Autowired GlobalExceptionHandler handler;
    @MockitoBean BrapiCatalogClient catalog;
    // These spies only pause real operations. Repositories, services and calculators are real.
    @MockitoSpyBean PortfolioAssetLockManager locks;
    @MockitoSpyBean IncomeMapper incomeMapper;
    @MockitoSpyBean TransactionMapper transactionMapper;
    private User user;
    private Portfolio portfolio, otherPortfolio;
    private Asset asset, otherAsset;
    private Broker broker;
    private MockMvc mvc;
    private ExecutorService threads;
    private CountDownLatch saved, release;
    private AtomicInteger holderPid;
    private BlockingQueue<Integer> waiters;
    private volatile boolean rollback;
    private static final String HOLDER = "income-holder", WAITER = "income-waiter";

    @BeforeEach void setUp() {
        threads = Executors.newFixedThreadPool(2);
        saved = new CountDownLatch(1); release = new CountDownLatch(1);
        holderPid = new AtomicInteger(); waiters = new LinkedBlockingQueue<>(); rollback = false;
        mvc = MockMvcBuilders.standaloneSetup(incomeController, transactionController).setControllerAdvice(handler).build();
        // Only the ephemeral Testcontainers database is cleared.
        jdbc.execute("TRUNCATE transactions, incomes, portfolio_asset_locks, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            user = users.save(new User("Income test", "income@test.local", "test"));
            portfolio = portfolios.save(new Portfolio("A", "Test", user));
            otherPortfolio = portfolios.save(new Portfolio("B", "Test", user));
            asset = asset("ITUB4"); otherAsset = asset("VALE3");
            broker = new Broker(); broker.setName("Test"); broker = brokers.save(broker);
        });
        PortfolioAssetLockManager target = AopTestUtils.getUltimateTargetObject(locks);
        doAnswer(inv -> {
            String actor = Thread.currentThread().getName();
            if (HOLDER.equals(actor) || WAITER.equals(actor)) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                int pid = ((Number) em.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue();
                if (HOLDER.equals(actor)) holderPid.set(pid); else waiters.add(pid);
            }
            return inv.callRealMethod();
        }).when(target).acquire(anyLong(), anyLong());
        doAnswer(inv -> pauseAfterSave(inv.callRealMethod())).when(incomeMapper).toResponse(any(Income.class), any(LocalDate.class));
        doAnswer(inv -> pauseAfterSave(inv.callRealMethod())).when(transactionMapper).toResponse(any(Transaction.class));
        transactions.create(request(portfolio, asset, BUY, "100.25", 1), user.getId());
    }
    @AfterEach void closeThreads() throws Exception {
        release.countDown(); threads.shutdown();
        if (!threads.awaitTermination(15, SECONDS)) { threads.shutdownNow(); assertTrue(threads.awaitTermination(5, SECONDS)); }
    }
    private Object pauseAfterSave(Object response) throws Exception {
        if (HOLDER.equals(Thread.currentThread().getName())) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            saved.countDown();
            if (!release.await(15, SECONDS)) throw new AssertionError("Holder not released");
            if (rollback) throw new ForcedRollback();
        }
        return response;
    }
    @ParameterizedTest @CsvSource({"SELL,50,5", "BUY,50,5", "SELL,0.25,10", "BUY,0.25,10"})
    void rejectsConflictingRetroactiveTransactionsWithoutChangingEitherRecord(TransactionType type, String quantity, int day) {
        var created = incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        IncomeResponse original = incomes.findById(created.id(), user.getId());
        long before = transactionRepository.count();
        assertThrows(IncomeEligibilityConflictException.class,
                () -> transactions.create(request(portfolio, asset, type, quantity, day), user.getId()));
        assertEquals(before, transactionRepository.count());
        assertEquals(original, incomes.findById(original.id(), user.getId()));
    }
    @Test void allowsRetroactiveTransactionsAfterBaseDateAndForOtherPairs() {
        var created = incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        IncomeResponse original = incomes.findById(created.id(), user.getId());
        transactions.create(request(portfolio, asset, SELL, "0.25", 11), user.getId());
        transactions.create(request(portfolio, asset, BUY, "1", 12), user.getId());
        transactions.create(request(portfolio, otherAsset, BUY, "10", 5), user.getId());
        transactions.create(request(otherPortfolio, asset, BUY, "10", 5), user.getId());
        assertEquals(original, incomes.findById(original.id(), user.getId()));
        assertEquals(5, transactionRepository.count());
    }
    @Test void usesSameDayOperationsAndFractionalQuantitiesWhenCreatingIncome() {
        transactions.create(request(portfolio, asset, SELL, "0.125", 10), user.getId());
        IncomeResponse result = incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        assertDecimal("100.125", result.quantity()); assertDecimal("50.0625", result.totalAmount());
    }
    @Test void preservesLegacySnapshotAndAllowsOnlyEffectiveAgreement() {
        // V9 assigned baseDate = paymentDate. Keep that snapshot, even if it differs from current history.
        Income legacy = new TransactionTemplate(manager).execute(status -> incomeRepository.save(new Income(
                portfolio, asset, IncomeType.DIVIDEND, new BigDecimal("0.50"), new BigDecimal("150.25"), date(10), date(10))));
        IncomeResponse original = incomes.findById(legacy.getId(), user.getId());
        transactions.create(request(portfolio, asset, BUY, "1", 11), user.getId());
        assertEquals(original, incomes.findById(legacy.getId(), user.getId()));
        transactions.create(request(portfolio, asset, BUY, "50", 5), user.getId());
        assertEquals(original, incomes.findById(legacy.getId(), user.getId()));
        assertThrows(IncomeEligibilityConflictException.class,
                () -> transactions.create(request(portfolio, asset, BUY, "1", 5), user.getId()));
        assertEquals(original, incomes.findById(legacy.getId(), user.getId()));
    }
    @Test void returns409ForIncomeConflictAnd400ForInvalidPaymentDate() throws Exception {
        when(catalog.confirm(asset.getTicker())).thenReturn(new InstrumentEvidence(
                asset.getTicker(), asset.getName(), asset.getType(), "B3", null, List.of(), "TEST", true));
        incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        mvc.perform(post("/api/v1/transactions").principal(principal()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"portfolioId":%d,"assetId":%d,"brokerId":%d,"type":"BUY","quantity":1,"unitPrice":10,"transactionDate":"2026-09-05"}
                                """.formatted(portfolio.getId(), asset.getId(), broker.getId())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Conflito com provento registrado"));
        mvc.perform(post("/api/v1/incomes").principal(principal()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"portfolioId":%d,"assetId":%d,"type":"DIVIDEND","amountPerUnit":0.5,"baseDate":"2026-09-10","paymentDate":"2026-09-09"}
                                """.formatted(portfolio.getId(), asset.getId())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("A data de pagamento não pode ser anterior à data-base do provento."));
        assertEquals(1, transactionRepository.count()); assertEquals(1, incomeRepository.count());
    }
    @Test void acceptsPaymentOnBaseDate() {
        var request = new IncomeRequest(portfolio.getId(), asset.getId(), IncomeType.DIVIDEND,
                new BigDecimal("0.50"), date(10), date(10));
        var result = incomes.create(request, user.getId());
        assertEquals(result.baseDate(), result.paymentDate()); assertTrue(result.received());
        assertDecimal("100.25", result.quantity());
    }
    @Test void zeroEligibilityRollsBackIncomeAndNewCoordinationRow() {
        assertThrows(NoPositionOnBaseDateException.class,
                () -> incomes.create(incomeRequest(portfolio, otherAsset, 10), user.getId()));
        assertEquals(0, incomeRepository.count());
        assertEquals(0L, jdbc.queryForObject("select count(*) from portfolio_asset_locks where portfolio_id=? and asset_id=?",
                Long.class, portfolio.getId(), otherAsset.getId()));
        transactions.create(request(portfolio, asset, SELL, "100.25", 5), user.getId());
        assertThrows(NoPositionOnBaseDateException.class, () -> incomes.create(incomeRequest(portfolio, asset, 10), user.getId()));
        assertEquals(0, incomeRepository.count());
    }
    @ParameterizedTest @CsvSource({"2026-10-30,true", "2026-10-31,true", "2026-11-01,false"})
    void classifiesPastTodayAndFutureWithoutChangingRegisteredAmount(String payment, boolean received) {
        var request = new IncomeRequest(portfolio.getId(), asset.getId(), IncomeType.DIVIDEND, new BigDecimal("0.50"), date(10), LocalDate.parse(payment));
        var response = incomes.create(request, user.getId());
        assertEquals(received, response.received()); assertDecimal("50.125", response.totalAmount());
        var stored = incomes.findById(response.id(), user.getId());
        assertEquals(received, stored.received());
        assertEquals(LocalDate.parse(payment), stored.paymentDate());
        assertDecimal("100.25", stored.quantity()); assertDecimal("50.125", stored.totalAmount());
        assertEquals(List.of(stored), incomes.findByPortfolioId(portfolio.getId(), user.getId()));
    }
    @Test void repositoryFiltersPortfolioAssetAndInclusiveBaseDateAndOrdersByDateAndId() {
        var later = incomes.create(incomeRequest(portfolio, asset, 20), user.getId());
        var first = incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        var second = incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        incomes.create(incomeRequest(portfolio, asset, 5), user.getId());
        transactions.create(request(otherPortfolio, asset, BUY, "1", 1), user.getId());
        incomes.create(incomeRequest(otherPortfolio, asset, 10), user.getId());
        transactions.create(request(portfolio, otherAsset, BUY, "1", 1), user.getId());
        incomes.create(incomeRequest(portfolio, otherAsset, 10), user.getId());
        assertEquals(List.of(first.id(), second.id(), later.id()), incomeRepository
                .findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(portfolio.getId(), asset.getId(), date(10))
                .stream().map(Income::getId).toList());
    }
    @ParameterizedTest @CsvSource({"true,false,SELL", "true,false,BUY", "false,false,SELL", "false,false,BUY", "true,true,SELL", "false,true,SELL"})
    void serializesIncomeAndRetroactiveTransactionWithRealPostgresBlocking(boolean incomeFirst, boolean forceRollback, TransactionType type) throws Exception {
        rollback = forceRollback;
        Callable<Object> createIncome = () -> incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
        Callable<Object> createTransaction = () -> transactions.create(request(portfolio, asset, type, "50", 5), user.getId());
        Future<Object> holder = submit(HOLDER, incomeFirst ? createIncome : createTransaction);
        assertTrue(saved.await(10, SECONDS));
        Future<Object> waiter = submit(WAITER, incomeFirst ? createTransaction : createIncome);
        Integer waiterPid = waiters.poll(10, SECONDS); assertNotNull(waiterPid);
        assertNotEquals(holderPid.get(), waiterPid);
        long deadline = System.nanoTime() + SECONDS.toNanos(4);
        boolean blocked = false;
        while (System.nanoTime() < deadline && !waiter.isDone()) {
            blocked = Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class, holderPid.get(), waiterPid));
            if (blocked) break;
            Thread.sleep(10);
        }
        assertTrue(blocked, "PostgreSQL must actually block the second connection");
        assertFalse(waiter.isDone());
        release.countDown();
        if (forceRollback) assertInstanceOf(ForcedRollback.class, assertThrows(ExecutionException.class, () -> holder.get(10, SECONDS)).getCause());
        else assertNotNull(holder.get(10, SECONDS));
        if (incomeFirst && !forceRollback) {
            assertInstanceOf(IncomeEligibilityConflictException.class, assertThrows(ExecutionException.class, () -> waiter.get(10, SECONDS)).getCause());
        } else assertNotNull(waiter.get(10, SECONDS));
        boolean transactionCommitted = incomeFirst ? forceRollback : !forceRollback;
        assertEquals(transactionCommitted ? 2 : 1, transactionRepository.count());
        if (incomeFirst && forceRollback) assertEquals(0, incomeRepository.count());
        else {
            var stored = incomes.findAll(user.getId()).getFirst();
            String expected = transactionCommitted ? (type == BUY ? "150.25" : "50.25") : "100.25";
            assertDecimal(expected, stored.quantity()); assertEquals(1, incomeRepository.count());
        }
    }
    @Test void incomeUsesIndependentTransactionWhenCallerRollsBack() {
        var result = new TransactionTemplate(manager).execute(status -> {
            var income = incomes.create(incomeRequest(portfolio, asset, 10), user.getId());
            status.setRollbackOnly(); return income;
        });
        assertNotNull(result); assertTrue(incomeRepository.existsById(result.id()));
    }
    private <T> Future<T> submit(String actor, Callable<T> action) {
        return threads.submit(() -> { String old = Thread.currentThread().getName(); Thread.currentThread().setName(actor);
            try { return action.call(); } finally { Thread.currentThread().setName(old); } });
    }
    private Asset asset(String ticker) { Asset value = new Asset(); value.setTicker(ticker); value.setName(ticker); value.setType(AssetType.STOCK); return assets.save(value); }
    private LocalDate date(int day) { return LocalDate.of(2026, 9, day); }
    private IncomeRequest incomeRequest(Portfolio p, Asset a, int day) { return new IncomeRequest(p.getId(), a.getId(), IncomeType.DIVIDEND, new BigDecimal("0.50"), date(day), date(25)); }
    private TransactionRequest request(Portfolio p, Asset a, TransactionType type, String quantity, int day) { return new TransactionRequest(p.getId(), a.getId(), broker.getId(), type, new BigDecimal(quantity), BigDecimal.TEN, date(day)); }
    private UsernamePasswordAuthenticationToken principal() { return new UsernamePasswordAuthenticationToken(user.getId().toString(), null, List.of()); }
    private void assertDecimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private static class ForcedRollback extends RuntimeException {}
}
