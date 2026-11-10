package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import java.sql.ResultSet;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioDeletionServiceTest {
    @SuppressWarnings("unchecked")
    private void mockOwnership(JdbcTemplate jdbc, boolean owned) {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L), eq(3L)))
                .thenReturn(owned);
    }

    @Test void deletesChildrenBeforePortfolioAndScopesEveryStep() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockOwnership(jdbc, true);
        when(jdbc.update(startsWith("DELETE FROM portfolios"), eq(7L), eq(3L))).thenReturn(1);
        new PortfolioDeletionService(jdbc).delete(7L, 3L);
        InOrder sequence = inOrder(jdbc);
        sequence.verify(jdbc).query(contains("FOR UPDATE"), any(ResultSetExtractor.class), eq(7L), eq(3L));
        sequence.verify(jdbc).update(contains("DELETE FROM b3_transaction_reversals"), eq(7L), eq(7L));
        sequence.verify(jdbc).update(contains("DELETE FROM b3_import_batch_rows"), eq(7L));
        sequence.verify(jdbc).update(startsWith("DELETE FROM b3_import_batches"), eq(7L));
        sequence.verify(jdbc).update(startsWith("DELETE FROM valuation_contracts"), eq(7L));
        sequence.verify(jdbc).update(startsWith("DELETE FROM portfolio_asset_locks"), eq(7L));
        sequence.verify(jdbc).update(startsWith("DELETE FROM incomes"), eq(7L));
        sequence.verify(jdbc).update(startsWith("DELETE FROM transactions"), eq(7L));
        sequence.verify(jdbc).update(startsWith("DELETE FROM portfolios"), eq(7L), eq(3L));
        sequence.verifyNoMoreInteractions();
    }

    @Test void doesNotDeleteAnotherUsersPortfolio() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockOwnership(jdbc, false);
        assertThrows(PortfolioNotFoundException.class,
                () -> new PortfolioDeletionService(jdbc).delete(7L, 3L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void failsTransactionWhenFinalDeleteIsNotCompleted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockOwnership(jdbc, true);
        assertThrows(IllegalStateException.class,
                () -> new PortfolioDeletionService(jdbc).delete(7L, 3L));
    }
}
