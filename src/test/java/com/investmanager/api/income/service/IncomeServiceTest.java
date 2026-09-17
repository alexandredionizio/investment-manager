package com.investmanager.api.income.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.income.exception.NoPositionOnBaseDateException;
import com.investmanager.api.income.mapper.IncomeMapper;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    private static final Long USER_ID = 2L;

    private static final LocalDate BASE_DATE =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PAYMENT_DATE =
            LocalDate.of(2026, 9, 3);

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private IncomeMapper incomeMapper;

    @Mock
    private PositionService positionService;

    private IncomeService incomeService;

    @BeforeEach
    void setUp() {

        incomeService = new IncomeService(
                incomeRepository,
                portfolioRepository,
                assetRepository,
                incomeMapper,
                positionService
        );
    }

    @Test
    void shouldCreateIncomeUsingPositionQuantityOnBaseDate() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        IncomeRequest request = new IncomeRequest(
                1L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                BASE_DATE,
                PAYMENT_DATE
        );

        PositionResponse position = new PositionResponse(
                1L,
                "ITUB4",
                new BigDecimal("100"),
                new BigDecimal("30.00"),
                new BigDecimal("3000.00")
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(positionService.calculatePositionByAssetAndDate(
                1L,
                1L,
                BASE_DATE
        )).thenReturn(position);

        Income savedIncome = new Income(
                portfolio,
                asset,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                BASE_DATE,
                PAYMENT_DATE
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
                BASE_DATE,
                PAYMENT_DATE
        );

        when(incomeMapper.toResponse(savedIncome))
                .thenReturn(expectedResponse);

        IncomeResponse result =
                incomeService.create(request, USER_ID);

        assertEquals(expectedResponse, result);

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verify(assetRepository)
                .findById(1L);

        verify(positionService)
                .calculatePositionByAssetAndDate(
                        1L,
                        1L,
                        BASE_DATE
                );

        verify(incomeRepository)
                .save(any(Income.class));

        verify(incomeMapper)
                .toResponse(savedIncome);
    }

    @Test
    void shouldThrowExceptionWhenPortfolioNotFound() {

        IncomeRequest request = new IncomeRequest(
                999L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                BASE_DATE,
                PAYMENT_DATE
        );

        when(portfolioRepository.findByIdAndUserId(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> incomeService.create(request, USER_ID)
        );

        verify(portfolioRepository)
                .findByIdAndUserId(999L, USER_ID);

        verifyNoInteractions(assetRepository);
        verifyNoInteractions(positionService);
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
                BASE_DATE,
                PAYMENT_DATE
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AssetNotFoundException.class,
                () -> incomeService.create(request, USER_ID)
        );

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verify(assetRepository)
                .findById(999L);

        verifyNoInteractions(positionService);

        verify(incomeRepository, never())
                .save(any(Income.class));

        verifyNoInteractions(incomeMapper);
    }

    @Test
    void shouldThrowExceptionWhenPositionDoesNotExistOnBaseDate() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        IncomeRequest request = new IncomeRequest(
                1L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                BASE_DATE,
                PAYMENT_DATE
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(positionService.calculatePositionByAssetAndDate(
                1L,
                1L,
                BASE_DATE
        )).thenReturn(null);

        assertThrows(
                NoPositionOnBaseDateException.class,
                () -> incomeService.create(request, USER_ID)
        );

        verify(positionService)
                .calculatePositionByAssetAndDate(
                        1L,
                        1L,
                        BASE_DATE
                );

        verify(incomeRepository, never())
                .save(any(Income.class));

        verifyNoInteractions(incomeMapper);
    }

    @Test
    void shouldThrowExceptionWhenPositionIsZeroOnBaseDate() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        IncomeRequest request = new IncomeRequest(
                1L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                BASE_DATE,
                PAYMENT_DATE
        );

        PositionResponse position = new PositionResponse(
                1L,
                "ITUB4",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(positionService.calculatePositionByAssetAndDate(
                1L,
                1L,
                BASE_DATE
        )).thenReturn(position);

        assertThrows(
                NoPositionOnBaseDateException.class,
                () -> incomeService.create(request, USER_ID)
        );

        verify(positionService)
                .calculatePositionByAssetAndDate(
                        1L,
                        1L,
                        BASE_DATE
                );

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
                BASE_DATE,
                PAYMENT_DATE
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
                BASE_DATE,
                PAYMENT_DATE
        );

        when(incomeRepository.findByIdAndPortfolioUserId(1L, USER_ID))
                .thenReturn(Optional.of(income));

        when(incomeMapper.toResponse(income))
                .thenReturn(expectedResponse);

        IncomeResponse result =
                incomeService.findById(1L, USER_ID);

        assertEquals(expectedResponse, result);

        verify(incomeRepository)
                .findByIdAndPortfolioUserId(1L, USER_ID);

        verify(incomeMapper)
                .toResponse(income);
    }

    @Test
    void shouldThrowExceptionWhenIncomeNotFound() {

        when(incomeRepository.findByIdAndPortfolioUserId(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IncomeNotFoundException.class,
                () -> incomeService.findById(999L, USER_ID)
        );

        verify(incomeRepository)
                .findByIdAndPortfolioUserId(999L, USER_ID);

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
                BASE_DATE,
                PAYMENT_DATE
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
                BASE_DATE,
                PAYMENT_DATE
        );

        when(incomeRepository.findAllByPortfolioUserId(USER_ID))
                .thenReturn(List.of(income));

        when(incomeMapper.toResponse(income))
                .thenReturn(response);

        List<IncomeResponse> result =
                incomeService.findAll(USER_ID);

        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());

        verify(incomeRepository)
                .findAllByPortfolioUserId(USER_ID);

        verify(incomeMapper)
                .toResponse(income);
    }

    @Test
    void shouldFindIncomesByPortfolioId() {

        Portfolio portfolio = new Portfolio();

        Income income = new Income(
                portfolio,
                new Asset(),
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                BASE_DATE,
                PAYMENT_DATE
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
                BASE_DATE,
                PAYMENT_DATE
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(incomeRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        1L,
                        USER_ID
                ))
                .thenReturn(List.of(income));

        when(incomeMapper.toResponse(income))
                .thenReturn(response);

        List<IncomeResponse> result =
                incomeService.findByPortfolioId(1L, USER_ID);

        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verify(incomeRepository)
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        1L,
                        USER_ID
                );

        verify(incomeMapper)
                .toResponse(income);
    }

    @Test
    void shouldThrowExceptionWhenPortfolioBelongsToAnotherUser() {

        IncomeRequest request = new IncomeRequest(
                1L,
                1L,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                BASE_DATE,
                PAYMENT_DATE
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> incomeService.create(request, USER_ID)
        );

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verifyNoInteractions(assetRepository);
        verifyNoInteractions(positionService);
        verifyNoInteractions(incomeMapper);
        verifyNoInteractions(incomeRepository);
    }
}