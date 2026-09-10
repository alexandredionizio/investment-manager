package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.exception.BrokerNotFoundException;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.position.service.PositionService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.dto.TransactionRequest;
import com.investmanager.api.transaction.dto.TransactionResponse;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import com.investmanager.api.transaction.mapper.TransactionMapper;
import com.investmanager.api.transaction.repository.TransactionRepository;
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
class TransactionServiceTest {

    private static final Long USER_ID = 2L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private PositionService positionService;

    @Mock
    private BrokerRepository brokerRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                portfolioRepository,
                assetRepository,
                brokerRepository,
                transactionMapper,
                positionService
        );
    }

    @Test
    void shouldCreateTransaction() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Broker broker = new Broker();
        broker.setName("XP Investimentos");

        TransactionRequest request = new TransactionRequest(
                1L,
                1L,
                1L,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(brokerRepository.findById(1L))
                .thenReturn(Optional.of(broker));

        Transaction savedTransaction = new Transaction(
                portfolio,
                asset,
                broker,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);

        TransactionResponse expectedResponse = new TransactionResponse(
                null,
                null,
                null,
                "ITUB4",
                1L,
                "XP Investimentos",
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(transactionMapper.toResponse(savedTransaction))
                .thenReturn(expectedResponse);

        TransactionResponse result =
                transactionService.create(request, USER_ID);

        assertEquals(expectedResponse, result);

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verify(assetRepository).findById(1L);
        verify(brokerRepository).findById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(transactionMapper).toResponse(savedTransaction);
    }

    @Test
    void shouldThrowExceptionWhenPortfolioNotFound() {

        TransactionRequest request = new TransactionRequest(
                999L,
                1L,
                1L,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(portfolioRepository.findByIdAndUserId(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> transactionService.create(request, USER_ID)
        );

        verify(portfolioRepository)
                .findByIdAndUserId(999L, USER_ID);

        verifyNoInteractions(assetRepository);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(brokerRepository);
    }

    @Test
    void shouldThrowExceptionWhenPortfolioBelongsToAnotherUser() {

        TransactionRequest request = new TransactionRequest(
                1L,
                1L,
                1L,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> transactionService.create(request, USER_ID)
        );

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verifyNoInteractions(assetRepository);
        verifyNoInteractions(brokerRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void shouldThrowExceptionWhenAssetNotFound() {

        Portfolio portfolio = new Portfolio();

        TransactionRequest request = new TransactionRequest(
                1L,
                999L,
                1L,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AssetNotFoundException.class,
                () -> transactionService.create(request, USER_ID)
        );

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verify(assetRepository).findById(999L);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(brokerRepository);
    }

    @Test
    void shouldFindTransactionById() {

        Transaction transaction = new Transaction(
                new Portfolio(),
                new Asset(),
                new Broker(),
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        TransactionResponse expectedResponse = new TransactionResponse(
                null,
                null,
                null,
                "ITUB4",
                1L,
                "XP Investimentos",
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(transactionRepository
                .findByIdAndPortfolioUserId(1L, USER_ID))
                .thenReturn(Optional.of(transaction));

        when(transactionMapper.toResponse(transaction))
                .thenReturn(expectedResponse);

        TransactionResponse result =
                transactionService.findById(1L, USER_ID);

        assertEquals(expectedResponse, result);

        verify(transactionRepository)
                .findByIdAndPortfolioUserId(1L, USER_ID);

        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void shouldThrowExceptionWhenTransactionNotFound() {

        when(transactionRepository
                .findByIdAndPortfolioUserId(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.findById(999L, USER_ID)
        );

        verify(transactionRepository)
                .findByIdAndPortfolioUserId(999L, USER_ID);

        verifyNoInteractions(transactionMapper);
    }

    @Test
    void shouldFindAllTransactions() {

        Transaction transaction = new Transaction(
                new Portfolio(),
                new Asset(),
                new Broker(),
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        TransactionResponse response = new TransactionResponse(
                null,
                null,
                null,
                "ITUB4",
                1L,
                "XP Investimentos",
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(transactionRepository.findAllByPortfolioUserId(USER_ID))
                .thenReturn(List.of(transaction));

        when(transactionMapper.toResponse(transaction))
                .thenReturn(response);

        List<TransactionResponse> result =
                transactionService.findAll(USER_ID);

        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());

        verify(transactionRepository)
                .findAllByPortfolioUserId(USER_ID);

        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void shouldFindTransactionsByPortfolioId() {

        Portfolio portfolio = new Portfolio();

        Transaction transaction = new Transaction(
                portfolio,
                new Asset(),
                new Broker(),
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        TransactionResponse response = new TransactionResponse(
                null,
                null,
                null,
                "ITUB4",
                1L,
                "XP Investimentos",
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(transactionRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                        1L,
                        USER_ID
                ))
                .thenReturn(List.of(transaction));

        when(transactionMapper.toResponse(transaction))
                .thenReturn(response);

        List<TransactionResponse> result =
                transactionService.findByPortfolioId(1L, USER_ID);

        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());

        verify(portfolioRepository)
                .findByIdAndUserId(1L, USER_ID);

        verify(transactionRepository)
                .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                        1L,
                        USER_ID
                );

        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void shouldThrowExceptionWhenSellingMoreThanAvailablePosition() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Broker broker = new Broker();
        broker.setName("XP Investimentos");

        TransactionRequest request = new TransactionRequest(
                1L,
                1L,
                1L,
                TransactionType.SELL,
                new BigDecimal("10"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 9, 4)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(brokerRepository.findById(1L))
                .thenReturn(Optional.of(broker));

        PositionResponse currentPosition = new PositionResponse(
                1L,
                "ITUB4",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        when(positionService.calculatePositionByAsset(1L, 1L))
                .thenReturn(currentPosition);

        assertThrows(
                InsufficientPositionException.class,
                () -> transactionService.create(request, USER_ID)
        );

        verify(transactionRepository, never())
                .save(any(Transaction.class));

        verify(brokerRepository).findById(1L);
    }

    @Test
    void shouldCreateSellTransactionWhenPositionIsSufficient() {

        Portfolio portfolio = new Portfolio();

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Broker broker = new Broker();
        broker.setName("XP Investimentos");

        TransactionRequest request = new TransactionRequest(
                1L,
                1L,
                1L,
                TransactionType.SELL,
                new BigDecimal("50"),
                new BigDecimal("45.00"),
                LocalDate.of(2026, 9, 2)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(brokerRepository.findById(1L))
                .thenReturn(Optional.of(broker));

        PositionResponse currentPosition = new PositionResponse(
                1L,
                "ITUB4",
                new BigDecimal("100"),
                new BigDecimal("37.50"),
                new BigDecimal("3750.00")
        );

        when(positionService.calculatePositionByAsset(1L, 1L))
                .thenReturn(currentPosition);

        Transaction savedTransaction = new Transaction(
                portfolio,
                asset,
                broker,
                TransactionType.SELL,
                new BigDecimal("50"),
                new BigDecimal("45.00"),
                LocalDate.of(2026, 9, 2)
        );

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);

        TransactionResponse expectedResponse = new TransactionResponse(
                null,
                null,
                null,
                "ITUB4",
                1L,
                "XP Investimentos",
                TransactionType.SELL,
                new BigDecimal("50"),
                new BigDecimal("45.00"),
                LocalDate.of(2026, 9, 2)
        );

        when(transactionMapper.toResponse(savedTransaction))
                .thenReturn(expectedResponse);

        TransactionResponse result =
                transactionService.create(request, USER_ID);

        assertEquals(expectedResponse, result);

        verify(transactionRepository)
                .save(any(Transaction.class));

        verify(brokerRepository).findById(1L);
    }

    @Test
    void shouldThrowExceptionWhenBrokerNotFound() {

        Portfolio portfolio = new Portfolio();
        Asset asset = new Asset();

        TransactionRequest request = new TransactionRequest(
                1L,
                1L,
                999L,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        when(portfolioRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(portfolio));

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        when(brokerRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                BrokerNotFoundException.class,
                () -> transactionService.create(request, USER_ID)
        );

        verify(brokerRepository).findById(999L);

        verify(transactionRepository, never())
                .save(any(Transaction.class));

        verifyNoInteractions(positionService);
    }
}