package com.investmanager.api.portfoliohistory.capital;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapitalMovementServiceTest {
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final CapitalMovementRepository movements = mock(CapitalMovementRepository.class);
    private final CapitalMovementService service = new CapitalMovementService(portfolios, movements);

    @Test void cannotListAnotherUsersPortfolio() {
        assertThrows(PortfolioNotFoundException.class, () -> service.list(7L, 22L));
        verifyNoInteractions(movements);
    }
    @Test void cannotCreateForAnotherUsersPortfolio() {
        var request = request(UUID.randomUUID(), new BigDecimal("100.00"));
        assertThrows(PortfolioNotFoundException.class, () -> service.create(7L, 22L, request));
        verifyNoInteractions(movements);
    }
    @Test void rejectsZeroTransfers() {
        when(portfolios.findByIdAndUserId(7L, 22L)).thenReturn(Optional.of(new Portfolio()));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(7L, 22L, request(UUID.randomUUID(), BigDecimal.ZERO)));
        verifyNoInteractions(movements);
    }
    @Test void rejectsExcessPrecision() {
        when(portfolios.findByIdAndUserId(7L, 22L)).thenReturn(Optional.of(new Portfolio()));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(7L, 22L, request(UUID.randomUUID(), new BigDecimal("1.001"))));
        verifyNoInteractions(movements);
    }
    @Test void duplicateRequestWithSamePayloadIsIdempotent() {
        var portfolio = new Portfolio(); var id = UUID.randomUUID(); var req = request(id, new BigDecimal("100.00"));
        var old = new CapitalMovement(portfolio, id, req.kind(), req.date(), req.amount(), req.description());
        when(portfolios.findByIdAndUserId(7L, 22L)).thenReturn(Optional.of(portfolio));
        when(movements.findByPortfolio_IdAndRequestId(7L, id)).thenReturn(Optional.of(old));
        assertEquals(0, service.create(7L, 22L, req).amount().compareTo(req.amount()));
        verify(movements, never()).saveAndFlush(any());
    }
    @Test void duplicateRequestWithChangedAmountIsRejected() {
        var portfolio = new Portfolio(); var id = UUID.randomUUID();
        var old = new CapitalMovement(portfolio, id, CapitalMovement.Kind.CONTRIBUTION, LocalDate.of(2026, 1, 1),
                new BigDecimal("100.00"), null);
        when(portfolios.findByIdAndUserId(7L, 22L)).thenReturn(Optional.of(portfolio));
        when(movements.findByPortfolio_IdAndRequestId(7L, id)).thenReturn(Optional.of(old));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(7L, 22L, request(id, new BigDecimal("200.00"))));
        verify(movements, never()).saveAndFlush(any());
    }
    private static CapitalMovementRequest request(UUID id, BigDecimal amount) {
        return new CapitalMovementRequest(id, CapitalMovement.Kind.CONTRIBUTION,
                LocalDate.of(2026, 1, 1), amount, null);
    }
}
