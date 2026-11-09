package com.investmanager.api.transaction.concurrency;

import com.investmanager.api.position.exception.InsufficientPositionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioAssetLockManagerTest {

    @ParameterizedTest
    @ValueSource(strings = {"55P03", "40P01"})
    void shouldRecognizeOnlyTransientLockSqlStates(String sqlState) {
        assertTrue(PortfolioAssetLockManager.isRetryableLockFailure(
                new DataAccessResourceFailureException("wrapped", new SQLException("failure", sqlState))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "08006", "40001", "57014"})
    void shouldNotRetryUnrelatedDatabaseFailures(String sqlState) {
        assertFalse(PortfolioAssetLockManager.isRetryableLockFailure(new SQLException("failure", sqlState)));
    }

    @Test
    void shouldInspectChainedSqlExceptions() {
        SQLException failure = new SQLException("wrapper");
        failure.setNextException(new SQLException("lock timeout", "55P03"));
        assertTrue(PortfolioAssetLockManager.isRetryableLockFailure(failure));
    }

    @Test
    void shouldNotClassifyBusinessErrorsAsTransient() {
        assertFalse(PortfolioAssetLockManager.isRetryableLockFailure(
                new InsufficientPositionException("ITUB4")));
    }
}
