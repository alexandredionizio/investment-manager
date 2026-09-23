package com.investmanager.api.benchmark.controller;

import com.investmanager.api.benchmark.dto.BenchmarkComparisonPointResponse;
import com.investmanager.api.benchmark.service.BenchmarkComparisonService;
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

class BenchmarkComparisonControllerTest {

    private BenchmarkComparisonService benchmarkComparisonService;
    private BenchmarkComparisonController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {

        benchmarkComparisonService =
                mock(BenchmarkComparisonService.class);

        authentication =
                mock(Authentication.class);

        controller =
                new BenchmarkComparisonController(
                        benchmarkComparisonService
                );

        when(authentication.getName())
                .thenReturn("10");
    }

    @Test
    void shouldReturnBenchmarkComparisonForCustomPeriod() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate startDate =
                LocalDate.of(2026, 1, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 22);

        List<BenchmarkComparisonPointResponse> expectedResponse =
                List.of(
                        new BenchmarkComparisonPointResponse(
                                startDate,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        ),
                        new BenchmarkComparisonPointResponse(
                                endDate,
                                new BigDecimal("0.15000000"),
                                new BigDecimal("0.10000000"),
                                new BigDecimal("0.12000000")
                        )
                );

        when(
                benchmarkComparisonService.getComparison(
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

        ResponseEntity<List<BenchmarkComparisonPointResponse>> response =
                controller.findComparison(
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
                benchmarkComparisonService
        ).getComparison(
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

        List<BenchmarkComparisonPointResponse> expectedResponse =
                List.of(
                        new BenchmarkComparisonPointResponse(
                                today,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        )
                );

        when(
                benchmarkComparisonService.getComparison(
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

        ResponseEntity<List<BenchmarkComparisonPointResponse>> response =
                controller.findComparison(
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
                benchmarkComparisonService
        ).getComparison(
                portfolioId,
                userId,
                PortfolioHistoryPeriod.ONE_YEAR,
                null,
                null,
                today
        );
    }
}