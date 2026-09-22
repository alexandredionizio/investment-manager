package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioReturnControllerTest {

    private PortfolioReturnService portfolioReturnService;
    private PortfolioReturnController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {

        portfolioReturnService =
                mock(PortfolioReturnService.class);

        authentication =
                mock(Authentication.class);

        controller =
                new PortfolioReturnController(
                        portfolioReturnService
                );

        when(authentication.getName())
                .thenReturn("10");
    }

    @Test
    void shouldReturnPortfolioReturnsForCustomPeriod() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate startDate =
                LocalDate.of(2026, 1, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 21);

        PortfolioReturnResponse expectedResponse =
                new PortfolioReturnResponse(
                        portfolioId,
                        startDate,
                        endDate,
                        new BigDecimal("0.1500000000"),
                        List.of()
                );

        when(
                portfolioReturnService.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate,
                        null
                )
        ).thenReturn(
                expectedResponse
        );

        ResponseEntity<PortfolioReturnResponse> response =
                controller.findReturns(
                        portfolioId,
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate,
                        authentication
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertSame(
                expectedResponse,
                response.getBody()
        );

        verify(
                portfolioReturnService
        ).getReturns(
                portfolioId,
                userId,
                PortfolioHistoryPeriod.CUSTOM,
                startDate,
                endDate,
                null
        );
    }

    @Test
    void shouldUseCurrentDateAsReferenceForNonCustomPeriod() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate today =
                LocalDate.now();

        PortfolioReturnResponse expectedResponse =
                new PortfolioReturnResponse(
                        portfolioId,
                        today.minusYears(1),
                        today,
                        new BigDecimal("0.1200000000"),
                        List.of()
                );

        when(
                portfolioReturnService.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        today
                )
        ).thenReturn(
                expectedResponse
        );

        ResponseEntity<PortfolioReturnResponse> response =
                controller.findReturns(
                        portfolioId,
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        authentication
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertSame(
                expectedResponse,
                response.getBody()
        );

        verify(
                portfolioReturnService
        ).getReturns(
                portfolioId,
                userId,
                PortfolioHistoryPeriod.ONE_YEAR,
                null,
                null,
                today
        );
    }
}