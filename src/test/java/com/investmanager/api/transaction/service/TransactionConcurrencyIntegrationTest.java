package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.shared.exception.GlobalExceptionHandler;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockAcquisitionException;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.controller.TransactionController;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.mapper.TransactionMapper;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.investmanager.api.transaction.TransactionType.BUY;
import static com.investmanager.api.transaction.TransactionType.SELL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "investment.transactions.concurrency.lock-timeout-ms=750",
        "investment.transactions.concurrency.max-attempts=3",
        "investment.transactions.concurrency.initial-backoff-ms=100",
        "investment.transactions.concurrency.max-backoff-ms=200"
})
@Testcontainers
class TransactionConcurrencyIntegrationTest {

    private static final String HOLDER = "concurrency-holder";
    private static final String CONTENDER = "concurrency-contender";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private TransactionService service;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private BrokerRepository brokerRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private TransactionController controller;
    @Autowired private GlobalExceptionHandler exceptionHandler;

    // Spies somente observam/pausam chamadas reais. Nenhum repository ou cálculo é substituído.
    @MockitoSpyBean private PortfolioAssetLockManager lockManager;
    @MockitoSpyBean private TransactionMapper mapper;

    private ExecutorService threads;
    private CountDownLatch holderSaved;
    private CountDownLatch releaseHolder;
    private volatile boolean rollbackHolder;
    private final List<Attempt> attempts = new CopyOnWriteArrayList<>();
    private BlockingQueue<Attempt> contenderAttempts;
    private Fixture fixture;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        threads = Executors.newFixedThreadPool(2);
        holderSaved = new CountDownLatch(1);
        releaseHolder = new CountDownLatch(1);
        rollbackHolder = false;
        attempts.clear();
        contenderAttempts = new LinkedBlockingQueue<>();
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(exceptionHandler).build();

        PortfolioAssetLockManager target = AopTestUtils.getUltimateTargetObject(lockManager);
        doAnswer(invocation -> {
            String actor = Thread.currentThread().getName();
            if (!HOLDER.equals(actor) && !CONTENDER.equals(actor)) return invocation.callRealMethod();
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(entityManager.isJoinedToTransaction());
            assertEquals("read committed", entityManager.createNativeQuery("show transaction_isolation")
                    .getSingleResult());
            int pid = ((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                    .getSingleResult()).intValue();
            String transactionId = entityManager.createNativeQuery("select pg_current_xact_id()::text")
                    .getSingleResult().toString();
            Attempt attempt = new Attempt(actor, pid, transactionId, new CompletableFuture<>());
            attempts.add(attempt);
            if (CONTENDER.equals(actor)) contenderAttempts.add(attempt);
            try {
                Object result = invocation.callRealMethod();
                attempt.outcome().complete(null);
                return result;
            } catch (Throwable failure) {
                attempt.outcome().complete(failure);
                throw failure;
            }
        }).when(target).acquire(anyLong(), anyLong());

        doAnswer(invocation -> {
            Object response = invocation.callRealMethod();
            if (HOLDER.equals(Thread.currentThread().getName())) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("0", entityManager.createNativeQuery("show lock_timeout").getSingleResult());
                holderSaved.countDown();
                if (!releaseHolder.await(15, SECONDS)) throw new AssertionError("Holder não foi liberado");
                if (rollbackHolder) throw new IntentionalRollbackException();
            }
            return response;
        }).when(mapper).toResponse(any(Transaction.class));

        // Exclusivamente o banco efêmero fornecido por @ServiceConnection.
        jdbc.execute("TRUNCATE transactions, incomes, portfolio_asset_locks, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        fixture = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.save(new User("User A", "a@test.com", "test"));
            User otherUser = userRepository.save(new User("User B", "b@test.com", "test"));
            Portfolio portfolio = portfolioRepository.save(new Portfolio("A", "Test", user));
            Portfolio otherPortfolio = portfolioRepository.save(new Portfolio("B", "Test", otherUser));
            Asset asset = saveAsset("ITUB4");
            Asset otherAsset = saveAsset("VALE3");
            Broker broker = new Broker();
            broker.setName("Broker");
            brokerRepository.save(broker);
            return new Fixture(user, otherUser, portfolio, otherPortfolio, asset, otherAsset, broker);
        });
        service.create(request(fixture.portfolio(), fixture.asset(), BUY, "100", 1), fixture.user().getId());
        service.create(request(fixture.portfolio(), fixture.otherAsset(), BUY, "100", 1), fixture.user().getId());
        service.create(request(fixture.otherPortfolio(), fixture.asset(), BUY, "100", 1), fixture.otherUser().getId());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        releaseHolder.countDown();
        threads.shutdown();
        if (!threads.awaitTermination(10, SECONDS)) {
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(10, SECONDS), "Threads de teste não terminaram");
        }
    }

    @Test
    void shouldAcceptOnlyOneOfTwoConcurrentSalesAndActuallyBlockTheOther() throws Exception {
        Future<TransactionResponse> a = startHolder(primary(SELL, "80", 2));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(primary(SELL, "80", 2), fixture.user().getId()));
        Attempt waiting = nextContenderAttempt();
        awaitBlocked(waiting, holderAttempt());
        assertFalse(a.isDone());
        assertFalse(b.isDone());
        assertEquals(0, saleCount(fixture.portfolio(), fixture.asset()), "A ainda não fez commit");

        releaseHolder.countDown();
        assertNotNull(a.get(10, SECONDS));
        assertFutureFailure(InsufficientPositionException.class, b);
        assertEquals(1, saleCount(fixture.portfolio(), fixture.asset()));
        assertValidHistory(fixture.portfolio(), fixture.asset(), "20");
    }

    @Test
    void shouldAllowDifferentAssetsToCommitWhileOriginalPairIsLocked() throws Exception {
        assertIndependentPair(fixture.portfolio(), fixture.otherAsset(), fixture.user());
    }

    @Test
    void shouldAllowDifferentPortfoliosAndUsersToCommitWhileOriginalPairIsLocked() throws Exception {
        assertIndependentPair(fixture.otherPortfolio(), fixture.asset(), fixture.otherUser());
    }

    @Test
    void shouldRejectConcurrentRetroactiveSaleThatWouldInvalidateLaterHistory() throws Exception {
        service.create(primary(SELL, "60", 10), fixture.user().getId());
        service.create(primary(BUY, "100", 20), fixture.user().getId());
        Future<TransactionResponse> a = startHolder(primary(SELL, "30", 5));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(primary(SELL, "30", 6), fixture.user().getId()));
        awaitBlocked(nextContenderAttempt(), holderAttempt());
        releaseHolder.countDown();

        a.get(10, SECONDS);
        assertFutureFailure(InsufficientPositionException.class, b);
        assertEquals(2, saleCount(fixture.portfolio(), fixture.asset()));
        assertValidHistory(fixture.portfolio(), fixture.asset(), "110");
    }

    @Test
    void shouldKeepExistingIdOrderWhenConcurrentOperationsHaveSameDate() throws Exception {
        Future<TransactionResponse> a = startHolder(primary(BUY, "40", 1));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(primary(SELL, "140", 1), fixture.user().getId()));
        awaitBlocked(nextContenderAttempt(), holderAttempt());
        releaseHolder.countDown();
        TransactionResponse purchase = a.get(10, SECONDS);
        TransactionResponse sale = b.get(10, SECONDS);

        List<Transaction> history = history(fixture.portfolio(), fixture.asset());
        assertEquals(List.of(BUY, BUY, SELL), history.stream().map(Transaction::getType).toList());
        assertTrue(purchase.id() < sale.id());
        assertEquals(sale.id(), history.getLast().getId());
        assertValidHistory(fixture.portfolio(), fixture.asset(), "0");
    }

    @Test
    void shouldCreateCoordinationRowIdempotentlyDuringConcurrentFirstUse() throws Exception {
        Asset newAsset = new TransactionTemplate(transactionManager).execute(status -> saveAsset("NEW3"));
        assertEquals(0, lockCount(fixture.portfolio(), newAsset));
        Future<TransactionResponse> a = startHolder(request(fixture.portfolio(), newAsset, BUY, "40", 1));
        Future<TransactionResponse> b = submit(CONTENDER, () -> service.create(
                request(fixture.portfolio(), newAsset, BUY, "60", 1), fixture.user().getId()));
        awaitBlocked(nextContenderAttempt(), holderAttempt());
        releaseHolder.countDown();

        a.get(10, SECONDS);
        b.get(10, SECONDS);
        assertEquals(1, lockCount(fixture.portfolio(), newAsset));
        assertEquals(2, history(fixture.portfolio(), newAsset).size());
        assertValidHistory(fixture.portfolio(), newAsset, "100");
    }

    @Test
    void shouldReleaseLockOnRollbackAndIgnoreUncommittedSale() throws Exception {
        rollbackHolder = true;
        Future<TransactionResponse> a = startHolder(primary(SELL, "80", 2));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(primary(SELL, "80", 2), fixture.user().getId()));
        awaitBlocked(nextContenderAttempt(), holderAttempt());
        releaseHolder.countDown();

        assertFutureFailure(IntentionalRollbackException.class, a);
        TransactionResponse accepted = b.get(10, SECONDS);
        assertEquals(1, saleCount(fixture.portfolio(), fixture.asset()));
        assertEquals(accepted.id(), history(fixture.portfolio(), fixture.asset()).getLast().getId());
        assertValidHistory(fixture.portfolio(), fixture.asset(), "20");
    }

    @Test
    void shouldRetryTimeoutInNewTransactionAndPersistOnlyOnce() throws Exception {
        Future<TransactionResponse> a = startHolder(primary(BUY, "40", 2));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(primary(SELL, "80", 3), fixture.user().getId()));
        Attempt first = nextContenderAttempt();
        awaitBlocked(first, holderAttempt());
        assertInstanceOf(PortfolioAssetLockAcquisitionException.class, first.outcome().get(10, SECONDS));
        Attempt second = nextContenderAttempt();
        awaitBlocked(second, holderAttempt());
        assertNotEquals(first.transactionId(), second.transactionId());
        releaseHolder.countDown();

        a.get(10, SECONDS);
        TransactionResponse accepted = b.get(10, SECONDS);
        assertEquals(2, contenderAttemptList().size());
        assertEquals(1, saleCount(fixture.portfolio(), fixture.asset()));
        assertEquals(accepted.id(), history(fixture.portfolio(), fixture.asset()).getLast().getId());
        assertValidHistory(fixture.portfolio(), fixture.asset(), "60");
    }

    @Test
    void shouldRereadChangedHistoryOnRetryAndNotRetryBusinessRejection() throws Exception {
        Future<TransactionResponse> a = startHolder(primary(SELL, "80", 2));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(primary(SELL, "80", 3), fixture.user().getId()));
        Attempt first = nextContenderAttempt();
        awaitBlocked(first, holderAttempt());
        assertInstanceOf(PortfolioAssetLockAcquisitionException.class, first.outcome().get(10, SECONDS));
        Attempt second = nextContenderAttempt();
        awaitBlocked(second, holderAttempt());
        assertNotEquals(first.transactionId(), second.transactionId());
        releaseHolder.countDown();

        a.get(10, SECONDS);
        assertFutureFailure(InsufficientPositionException.class, b);
        assertEquals(2, contenderAttemptList().size(), "Erro de negócio não pode provocar terceira tentativa");
        assertEquals(1, saleCount(fixture.portfolio(), fixture.asset()));
        assertValidHistory(fixture.portfolio(), fixture.asset(), "20");
    }

    @Test
    void shouldReturn503AfterExhaustingLockTimeoutsWithoutDuplicatingOperations() throws Exception {
        Future<TransactionResponse> a = startHolder(primary(SELL, "10", 2));
        Future<MvcResult> response = submit(CONTENDER, () -> mvc.perform(post("/api/v1/transactions")
                        .principal(UsernamePasswordAuthenticationToken.authenticated(
                                fixture.user().getId().toString(), null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"portfolioId":%d,"assetId":%d,"brokerId":%d,"type":"SELL",
                                 "quantity":80,"unitPrice":1,"transactionDate":"2026-09-03"}
                                """.formatted(fixture.portfolio().getId(), fixture.asset().getId(), fixture.broker().getId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Serviço temporariamente indisponível"))
                .andExpect(jsonPath("$.message").value("Não foi possível registrar a operação devido à concorrência. Tente novamente."))
                .andExpect(header().doesNotExist("Retry-After"))
                .andReturn());
        awaitBlocked(nextContenderAttempt(), holderAttempt());
        response.get(10, SECONDS);
        assertFalse(a.isDone());
        assertFailedAttemptsAreDistinct(3);
        assertEquals(0, saleCount(fixture.portfolio(), fixture.asset()));
        releaseHolder.countDown();
        a.get(10, SECONDS);
        assertEquals(1, saleCount(fixture.portfolio(), fixture.asset()));
        assertValidHistory(fixture.portfolio(), fixture.asset(), "90");
    }

    @Test
    void shouldBoundTimeoutsEvenWhenWaitingToInsertTheFirstCoordinationRow() throws Exception {
        Asset newAsset = new TransactionTemplate(transactionManager).execute(status -> saveAsset("FIRST3"));
        Future<TransactionResponse> a = startHolder(request(fixture.portfolio(), newAsset, BUY, "40", 1));
        Future<TransactionResponse> b = submit(CONTENDER, () -> service.create(
                request(fixture.portfolio(), newAsset, BUY, "60", 1), fixture.user().getId()));
        awaitBlocked(nextContenderAttempt(), holderAttempt());
        assertFutureFailure(com.investmanager.api.transaction.exception.TransactionConcurrencyException.class, b);
        assertFailedAttemptsAreDistinct(3);
        releaseHolder.countDown();
        a.get(10, SECONDS);
        assertEquals(1, lockCount(fixture.portfolio(), newAsset));
        assertEquals(1, history(fixture.portfolio(), newAsset).size());
        assertValidHistory(fixture.portfolio(), newAsset, "40");
    }

    @Test
    void shouldNotRetryInsufficientPositionWithoutAnyLockContention() throws Exception {
        Future<TransactionResponse> result = submit(CONTENDER,
                () -> service.create(primary(SELL, "101", 2), fixture.user().getId()));
        assertFutureFailure(InsufficientPositionException.class, result);
        assertEquals(1, contenderAttemptList().size());
        assertEquals(0, saleCount(fixture.portfolio(), fixture.asset()));
        assertValidHistory(fixture.portfolio(), fixture.asset(), "100");
    }

    @Test
    void shouldStartIndependentTransactionEvenWhenCallerAlreadyHasOne() throws Exception {
        AtomicReference<String> outerId = new AtomicReference<>();
        Future<TransactionResponse> result = submit(CONTENDER, () ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    outerId.set(entityManager.createNativeQuery("select pg_current_xact_id()::text")
                            .getSingleResult().toString());
                    TransactionResponse created = service.create(primary(SELL, "10", 2), fixture.user().getId());
                    status.setRollbackOnly();
                    return created;
                }));
        assertNotNull(result.get(10, SECONDS));
        assertNotEquals(outerId.get(), contenderAttemptList().getFirst().transactionId());
        assertEquals(1, saleCount(fixture.portfolio(), fixture.asset()));
        assertValidHistory(fixture.portfolio(), fixture.asset(), "90");
    }

    @Test
    void shouldApplyAndValidateV11AndV12() {
        flyway.validate();
        assertTrue(Integer.parseInt(flyway.info().current().getVersion().getVersion()) >= 16,
                "O banco de testes deve ter aplicado a migração V16 ou posterior");
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "select success from flyway_schema_history where version = '16'", Boolean.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select success from flyway_schema_history where version = '13'", Boolean.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "select success from flyway_schema_history where version = '11'", Boolean.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "select success from flyway_schema_history where version = '12'", Boolean.class));
    }

    private void assertIndependentPair(Portfolio portfolio, Asset asset, User user) throws Exception {
        Future<TransactionResponse> a = startHolder(primary(SELL, "80", 2));
        Future<TransactionResponse> b = submit(CONTENDER,
                () -> service.create(request(portfolio, asset, SELL, "80", 2), user.getId()));
        TransactionResponse accepted = b.get(10, SECONDS);
        assertNotNull(accepted);
        assertFalse(a.isDone(), "O primeiro par precisa continuar bloqueado durante o commit do outro");
        assertEquals(1, releaseHolder.getCount());
        assertEquals(1, contenderAttemptList().size());
        assertValidHistory(portfolio, asset, "20");
        releaseHolder.countDown();
        a.get(10, SECONDS);
    }

    private Future<TransactionResponse> startHolder(TransactionRequest request) throws Exception {
        Future<TransactionResponse> result = submit(HOLDER, () -> service.create(request, fixture.user().getId()));
        assertTrue(holderSaved.await(10, SECONDS), "A não alcançou o save dentro da transação");
        return result;
    }

    private <T> Future<T> submit(String name, Callable<T> action) {
        return threads.submit(() -> {
            String previousName = Thread.currentThread().getName();
            Thread.currentThread().setName(name);
            try { return action.call(); }
            finally { Thread.currentThread().setName(previousName); }
        });
    }

    private Attempt holderAttempt() {
        return attempts.stream().filter(a -> HOLDER.equals(a.actor())).findFirst().orElseThrow();
    }

    private Attempt nextContenderAttempt() throws InterruptedException {
        Attempt attempt = contenderAttempts.poll(10, SECONDS);
        assertNotNull(attempt, "B não iniciou a tentativa");
        return attempt;
    }

    private List<Attempt> contenderAttemptList() {
        return attempts.stream().filter(a -> CONTENDER.equals(a.actor())).toList();
    }

    private void awaitBlocked(Attempt waiting, Attempt holder) throws InterruptedException {
        assertNotEquals(holder.pid(), waiting.pid(), "Requisições precisam de conexões independentes");
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "select ? = any(pg_blocking_pids(?))", Boolean.class, holder.pid(), waiting.pid()))) return;
            if (waiting.outcome().isDone()) fail("B terminou a aquisição antes de comprovar bloqueio no PostgreSQL");
            Thread.sleep(10);
        }
        fail("PostgreSQL não registrou B aguardando A");
    }

    private void assertFailedAttemptsAreDistinct(int expected) throws Exception {
        List<Attempt> recorded = contenderAttemptList();
        assertEquals(expected, recorded.size());
        assertEquals(expected, recorded.stream().map(Attempt::transactionId).distinct().count());
        for (Attempt attempt : recorded) {
            assertInstanceOf(PortfolioAssetLockAcquisitionException.class, attempt.outcome().get(1, SECONDS));
        }
    }

    private <T extends Throwable> void assertFutureFailure(Class<T> type, Future<?> future) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
        assertInstanceOf(type, failure.getCause());
    }

    private TransactionRequest primary(TransactionType type, String quantity, int day) {
        return request(fixture.portfolio(), fixture.asset(), type, quantity, day);
    }

    private TransactionRequest request(Portfolio portfolio, Asset asset, TransactionType type, String quantity, int day) {
        return new TransactionRequest(portfolio.getId(), asset.getId(), fixture.broker().getId(), type,
                new BigDecimal(quantity), BigDecimal.ONE, LocalDate.of(2026, 9, day));
    }

    private Asset saveAsset(String ticker) {
        Asset asset = new Asset();
        asset.setTicker(ticker);
        asset.setName(ticker);
        asset.setType(AssetType.STOCK);
        return assetRepository.save(asset);
    }

    private List<Transaction> history(Portfolio portfolio, Asset asset) {
        return transactionRepository.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(
                portfolio.getId(), asset.getId());
    }

    private long saleCount(Portfolio portfolio, Asset asset) {
        return history(portfolio, asset).stream().filter(transaction -> transaction.getType() == SELL).count();
    }

    private long lockCount(Portfolio portfolio, Asset asset) {
        return jdbc.queryForObject("select count(*) from portfolio_asset_locks where portfolio_id = ? and asset_id = ?",
                Long.class, portfolio.getId(), asset.getId());
    }

    private void assertValidHistory(Portfolio portfolio, Asset asset, String expectedQuantity) {
        BigDecimal quantity = BigDecimal.ZERO;
        for (Transaction transaction : history(portfolio, asset)) {
            quantity = transaction.getType() == BUY
                    ? quantity.add(transaction.getQuantity()) : quantity.subtract(transaction.getQuantity());
            assertTrue(quantity.signum() >= 0, "Saldo negativo após operação " + transaction.getId());
        }
        assertEquals(0, quantity.compareTo(new BigDecimal(expectedQuantity)));
    }

    private record Attempt(String actor, int pid, String transactionId, CompletableFuture<Throwable> outcome) {}
    private record Fixture(User user, User otherUser, Portfolio portfolio, Portfolio otherPortfolio,
                           Asset asset, Asset otherAsset, Broker broker) {}
    private static class IntentionalRollbackException extends RuntimeException {}
}
