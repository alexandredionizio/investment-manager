package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.shared.integration.MarketDataCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.investmanager.api.benchmark.service.BenchmarkHistoryFixture.q;

@DataJpaTest
@Testcontainers
@Import({BenchmarkQuoteWriter.class,BenchmarkHistorySynchronizer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BenchmarkQuoteWriterIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired BenchmarkQuoteWriter writer;
    @Autowired BenchmarkHistorySynchronizer synchronizer;
    @Autowired BenchmarkQuoteRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean MarketDataCache cache;

    @Test void concurrentReplaysUseUniqueConstraintAndPreserveExistingValues() throws Exception {
        jdbc.update("DELETE FROM benchmark_quotes");
        var first=q(BenchmarkType.CDI,"2026-09-01","0.05");
        writer.insertMissing(List.of(first));
        var next=q(BenchmarkType.CDI,"2026-09-02","0.06");
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->{gate.await();writer.insertMissing(List.of(next,q(BenchmarkType.CDI,"2026-09-01","999")));return null;});
            var b=pool.submit(()->{gate.await();writer.insertMissing(List.of(first,next));return null;});
            gate.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }
        writer.insertMissing(List.of(first,next));
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByBenchmarkAndDate(BenchmarkType.CDI,first.getDate()).orElseThrow().getValue()).isEqualByComparingTo("0.05");
    }

    @Test void failedBatchRollsBackAllItsInserts() {
        jdbc.update("DELETE FROM benchmark_quotes");
        writer.insertMissing(List.of(q(BenchmarkType.CDI,"2026-09-01","0.05")));
        var valid=q(BenchmarkType.CDI,"2026-09-02","0.06");
        var invalid=new BenchmarkQuote(BenchmarkType.CDI,LocalDate.of(2026,9,3),null);
        assertThatThrownBy(()->writer.insertMissing(List.of(valid,invalid))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test void concurrentSynchronizationsSuspendAmbientTransactionsAroundHttp() throws Exception {
        jdbc.update("DELETE FROM benchmark_quotes");
        var first=q(BenchmarkType.CDI,"2026-09-01","0.05");
        var last=q(BenchmarkType.CDI,"2026-09-03","0.07");
        var missing=q(BenchmarkType.CDI,"2026-09-02","0.06");
        writer.insertMissing(List.of(first,last));
        var bothFetching=new CyclicBarrier(2);
        Callable<List<BenchmarkQuote>> run=()->new TransactionTemplate(transactions).execute(status->
                synchronizer.load(BenchmarkType.CDI,first.getDate(),last.getDate(),(a,b)->{
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    try { bothFetching.await(10,TimeUnit.SECONDS); } catch(Exception error) {throw new RuntimeException(error);}
                    return List.of(last,missing,first,missing);
                }));
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(run);var b=pool.submit(run);
            assertThat(a.get(30,TimeUnit.SECONDS)).hasSize(3);
            assertThat(b.get(30,TimeUnit.SECONDS)).hasSize(3);
        }
        assertThat(repository.count()).isEqualTo(3);
    }
}
