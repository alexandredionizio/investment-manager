package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.mapper.PortfolioMapper;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioMapper portfolioMapper;

    @Mock
    private UserRepository userRepository;

    private PortfolioService portfolioService;

    @BeforeEach
    void setup() {
        portfolioService = new PortfolioService(
                portfolioRepository,
                portfolioMapper,
                userRepository
        );
    }

    @Test
    void shouldReturnPortfolioWhenIdExistsForUser() {

        Long userId = 2L;
        Long portfolioId = 1L;

        User user = new User(
                "Alexandre",
                "teste@email.com",
                "senha"
        );

        Portfolio portfolio = new Portfolio(
                "Carteira Principal",
                "Carteira de longo prazo",
                user
        );

        PortfolioResponse expectedResponse = new PortfolioResponse(
                null,
                "Carteira Principal",
                "Carteira de longo prazo",
                portfolio.getCreatedAt()
        );

        when(portfolioRepository.findByIdAndUserId(portfolioId, userId))
                .thenReturn(Optional.of(portfolio));

        when(portfolioMapper.toResponse(portfolio))
                .thenReturn(expectedResponse);

        PortfolioResponse response =
                portfolioService.findById(portfolioId, userId);

        assertEquals("Carteira Principal", response.name());
        assertEquals(
                "Carteira de longo prazo",
                response.description()
        );
    }

    @Test
    void shouldThrowExceptionWhenPortfolioDoesNotExistForUser() {

        Long userId = 2L;
        Long portfolioId = 999L;

        when(portfolioRepository.findByIdAndUserId(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> portfolioService.findById(portfolioId, userId)
        );
    }

    @Test
    void shouldReturnAllPortfoliosForUser() {

        Long userId = 2L;

        User user = new User(
                "Alexandre",
                "teste@email.com",
                "senha"
        );

        Portfolio portfolio1 = new Portfolio(
                "Carteira Principal",
                "Carteira de longo prazo",
                user
        );

        Portfolio portfolio2 = new Portfolio(
                "Carteira Aposentadoria",
                "Carteira voltada para aposentadoria",
                user
        );

        PortfolioResponse response1 = new PortfolioResponse(
                null,
                "Carteira Principal",
                "Carteira de longo prazo",
                portfolio1.getCreatedAt()
        );

        PortfolioResponse response2 = new PortfolioResponse(
                null,
                "Carteira Aposentadoria",
                "Carteira voltada para aposentadoria",
                portfolio2.getCreatedAt()
        );

        when(portfolioRepository.findAllByUserId(userId))
                .thenReturn(List.of(portfolio1, portfolio2));

        when(portfolioMapper.toResponse(portfolio1))
                .thenReturn(response1);

        when(portfolioMapper.toResponse(portfolio2))
                .thenReturn(response2);

        List<PortfolioResponse> response =
                portfolioService.findAll(userId);

        assertEquals(2, response.size());
        assertEquals(
                "Carteira Principal",
                response.get(0).name()
        );
        assertEquals(
                "Carteira Aposentadoria",
                response.get(1).name()
        );
    }

    @Test
    void shouldCreatePortfolioForUser() {

        Long userId = 2L;

        CreatePortfolioRequest request =
                new CreatePortfolioRequest(
                        "Carteira Principal",
                        "Carteira de longo prazo"
                );

        User user = new User(
                "Alexandre",
                "teste@email.com",
                "senha"
        );

        Portfolio portfolio = new Portfolio();
        portfolio.setName("Carteira Principal");
        portfolio.setDescription("Carteira de longo prazo");
        portfolio.initializeCreatedAt();

        PortfolioResponse expectedResponse =
                new PortfolioResponse(
                        null,
                        "Carteira Principal",
                        "Carteira de longo prazo",
                        portfolio.getCreatedAt()
                );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(portfolioMapper.toEntity(request))
                .thenReturn(portfolio);

        when(portfolioRepository.save(any(Portfolio.class)))
                .thenReturn(portfolio);

        when(portfolioMapper.toResponse(portfolio))
                .thenReturn(expectedResponse);

        PortfolioResponse response =
                portfolioService.create(request, userId);

        assertSame(user, portfolio.getUser());
        assertEquals(
                "Carteira Principal",
                response.name()
        );
        assertEquals(
                "Carteira de longo prazo",
                response.description()
        );
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExistOnCreate() {

        Long userId = 999L;

        CreatePortfolioRequest request =
                new CreatePortfolioRequest(
                        "Carteira Principal",
                        "Carteira de longo prazo"
                );

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> portfolioService.create(request, userId)
        );
    }
}