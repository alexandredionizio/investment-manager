package com.investmanager.api.income.service;

import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.income.mapper.IncomeMapper;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.portfolio.Portfolio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.investmanager.api.asset.exception.AssetNotFoundException;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private IncomeMapper incomeMapper;

    private IncomeService incomeService;

    @BeforeEach
    void setUp() {
        incomeService = new IncomeService(
                incomeRepository,
                portfolioRepository,
                assetRepository,
                incomeMapper
        );
    }

    @Test
    void shouldCreateIncome() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        IncomeRequest request = new IncomeRequest(
                1L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        when(portfolioRepository.findById(1L))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        Income savedIncome = new Income(
                portfolio,
                asset,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        when(incomeRepository.save(any(Income.class)))
                .thenReturn(savedIncome);

        IncomeResponse expectedResponse = new IncomeResponse(
                null,
                null,
                null,
                "ITUB4",
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 9, 3)
        );

        when(incomeMapper.toResponse(savedIncome))
                .thenReturn(expectedResponse);

        IncomeResponse result = incomeService.create(request);

        assertEquals(expectedResponse, result);

        verify(portfolioRepository).findById(1L);
        verify(assetRepository).findById(1L);
        verify(incomeRepository).save(any(Income.class));
        verify(incomeMapper).toResponse(savedIncome);
    }

    @Test
    void shouldThrowExceptionWhenPortfolioNotFound() {

        IncomeRequest request = new IncomeRequest(
                999L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        when(portfolioRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> incomeService.create(request)
        );

        verify(portfolioRepository).findById(999L);
        verifyNoInteractions(assetRepository);
        verifyNoInteractions(incomeMapper);

        verify(incomeRepository, never())
                .save(any(Income.class));
    }

    @Test
    void shouldThrowExceptionWhenAssetNotFound() {

        Portfolio portfolio = new Portfolio();

        IncomeRequest request = new IncomeRequest(
                1L,
                999L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        when(portfolioRepository.findById(1L))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AssetNotFoundException.class,
                () -> incomeService.create(request)
        );

        verify(portfolioRepository).findById(1L);
        verify(assetRepository).findById(999L);

        verify(incomeRepository, never())
                .save(any(Income.class));

        verifyNoInteractions(incomeMapper);
    }

    @Test
    void shouldFindIncomeById() {

        Income income = new Income(
                new Portfolio(),
                new Asset(),
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        IncomeResponse expectedResponse = new IncomeResponse(
                1L,
                1L,
                1L,
                "ITUB4",
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 9, 3)
        );

        when(incomeRepository.findById(1L))
                .thenReturn(Optional.of(income));

        when(incomeMapper.toResponse(income))
                .thenReturn(expectedResponse);

        IncomeResponse result = incomeService.findById(1L);

        assertEquals(expectedResponse, result);

        verify(incomeRepository).findById(1L);
        verify(incomeMapper).toResponse(income);
    }

    @Test
    void shouldThrowExceptionWhenIncomeNotFound() {

        when(incomeRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                IncomeNotFoundException.class,
                () -> incomeService.findById(999L)
        );

        verify(incomeRepository).findById(999L);
        verifyNoInteractions(incomeMapper);
    }

    @Test
    void shouldFindAllIncomes() {

        Income income = new Income(
                new Portfolio(),
                new Asset(),
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        IncomeResponse response = new IncomeResponse(
                1L,
                1L,
                1L,
                "ITUB4",
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 9, 3)
        );

        when(incomeRepository.findAll())
                .thenReturn(List.of(income));

        when(incomeMapper.toResponse(income))
                .thenReturn(response);

        List<IncomeResponse> result =
                incomeService.findAll();

        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());

        verify(incomeRepository).findAll();
        verify(incomeMapper).toResponse(income);
    }

    @Test
    void shouldFindIncomesByPortfolioId() {

        Income income = new Income(
                new Portfolio(),
                new Asset(),
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        IncomeResponse response = new IncomeResponse(
                1L,
                1L,
                1L,
                "ITUB4",
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 9, 3)
        );

        when(incomeRepository
                .findByPortfolioIdOrderByPaymentDateAscIdAsc(1L))
                .thenReturn(List.of(income));

        when(incomeMapper.toResponse(income))
                .thenReturn(response);

        List<IncomeResponse> result =
                incomeService.findByPortfolioId(1L);

        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());

        verify(incomeRepository)
                .findByPortfolioIdOrderByPaymentDateAscIdAsc(1L);

        verify(incomeMapper).toResponse(income);
    }
}