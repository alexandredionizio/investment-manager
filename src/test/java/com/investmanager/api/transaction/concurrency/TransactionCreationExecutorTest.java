package com.investmanager.api.transaction.concurrency;

import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.transaction.exception.TransactionConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionCreationExecutorTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    private final List<TransactionStatus> transactions = new ArrayList<>();
    private TransactionCreationExecutor executor;

    @BeforeEach
    void setUp() {
        TransactionConcurrencyProperties properties = new TransactionConcurrencyProperties();
        properties.setInitialBackoffMs(1);
        properties.setMaxBackoffMs(2);
        executor = new TransactionCreationExecutor(transactionManager, properties);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel());
            TransactionStatus transaction = mock(TransactionStatus.class);
            transactions.add(transaction);
            return transaction;
        });
    }

    @Test
    void shouldCommitSuccessfulCreationOnce() {
        assertEquals("created", executor.execute(() -> "created"));
        assertEquals(1, transactions.size());
        verify(transactionManager).commit(transactions.getFirst());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldRollbackBeforeRetryingInNewTransactions() {
        AtomicInteger attempts = new AtomicInteger();
        assertEquals("created", executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) throw lockFailure();
            return "created";
        }));

        assertEquals(3, attempts.get());
        var order = inOrder(transactionManager);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).rollback(transactions.get(0));
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).rollback(transactions.get(1));
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(transactions.get(2));
        assertEquals(3, transactions.stream().distinct().count());
    }

    @Test
    void shouldStopAfterThreeFailedAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(TransactionConcurrencyException.class, () -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw lockFailure();
        }));
        assertEquals(3, attempts.get());
        verify(transactionManager, times(3)).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldNotRetryBusinessFailures() {
        InsufficientPositionException failure = new InsufficientPositionException("ITUB4");
        assertSame(failure, assertThrows(InsufficientPositionException.class,
                () -> executor.execute(() -> { throw failure; })));
        assertEquals(1, transactions.size());
        verify(transactionManager).rollback(transactions.getFirst());
    }

    @Test
    void shouldNotRetryLockErrorsOutsideTheAcquisitionPhase() {
        CannotAcquireLockException failure = new CannotAcquireLockException(
                "Falha ao persistir", new SQLException("lock timeout", "55P03"));
        assertSame(failure, assertThrows(CannotAcquireLockException.class,
                () -> executor.execute(() -> { throw failure; })));
        assertEquals(1, transactions.size());
        verify(transactionManager).rollback(transactions.getFirst());
    }

    @Test
    void shouldNotRepeatCreationWhenCommitFails() {
        CannotAcquireLockException failure = new CannotAcquireLockException("Falha no commit");
        doThrow(failure).when(transactionManager).commit(any());
        AtomicInteger creations = new AtomicInteger();
        assertSame(failure, assertThrows(CannotAcquireLockException.class,
                () -> executor.execute(creations::incrementAndGet)));
        assertEquals(1, creations.get());
        assertEquals(1, transactions.size());
    }

    @Test
    void shouldStopRetryingAndRestoreInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            TransactionConcurrencyException failure = assertThrows(TransactionConcurrencyException.class,
                    () -> executor.execute(() -> { throw lockFailure(); }));
            assertInstanceOf(InterruptedException.class, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, transactions.size());
        } finally {
            Thread.interrupted();
        }
    }

    private PortfolioAssetLockAcquisitionException lockFailure() {
        return new PortfolioAssetLockAcquisitionException(new SQLException("lock timeout", "55P03"));
    }
}
