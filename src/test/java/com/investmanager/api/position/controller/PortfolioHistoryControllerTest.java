package com.investmanager.api.portfoliohistory.controller;

import com.investmanager.api.portfoliohistory.dto.PortfolioHistoryResponse;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPoint;
import com.investmanager.api.portfoliohistory.service.PortfolioHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryControllerTest {

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock
    private PortfolioHistoryService portfolioHistoryService;

    private PortfolioHistoryController portfolioHistoryController;

    @BeforeEach
    void setUp() {

        portfolioHistoryController =
                new PortfolioHistoryController(
                        portfolioHistoryService
                );
    }

    @Test
    void shouldReturnPortfolioHistoryForCustomPeriod() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryResponse serviceResponse =
                new PortfolioHistoryResponse(
                        PORTFOLIO_ID,
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate,
                        PortfolioHistoryGranularity.DAILY,
                        List.of(
                                new PortfolioHistoryPoint(
                                        LocalDate.of(2026, 9, 1),
                                        new BigDecimal("4000.00")
                                ),
                                new PortfolioHistoryPoint(
                                        LocalDate.of(2026, 9, 18),
                                        new BigDecimal("4200.00")
                                )
                        )
                );

        when(portfolioHistoryService.getHistory(
                PORTFOLIO_ID,
                USER_ID,
                PortfolioHistoryPeriod.CUSTOM,
                startDate,
                endDate,
                null
        )).thenReturn(serviceResponse);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        USER_ID.toString(),
                        null
                );

        ResponseEntity<PortfolioHistoryResponse> response =
                portfolioHistoryController.findHistory(
                        PORTFOLIO_ID,
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate,
                        authentication
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertEquals(
                serviceResponse,
                response.getBody()
        );

        assertEquals(
                PORTFOLIO_ID,
                response.getBody().portfolioId()
        );

        assertEquals(
                PortfolioHistoryPeriod.CUSTOM,
                response.getBody().period()
        );

        assertEquals(
                PortfolioHistoryGranularity.DAILY,
                response.getBody().granularity()
        );

        assertEquals(
                2,
                response.getBody().points().size()
        );

        assertEquals(
                0,
                new BigDecimal("4200.00")
                        .compareTo(
                                response.getBody()
                                        .points()
                                        .getLast()
                                        .value()
                        )
        );

        verify(portfolioHistoryService)
                .getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate,
                        null
                );
    }
}