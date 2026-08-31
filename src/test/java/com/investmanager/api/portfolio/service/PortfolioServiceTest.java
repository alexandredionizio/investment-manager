package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.shared.exception.PortfolioNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    private PortfolioService portfolioService;

    @BeforeEach
    void Setup(){
        portfolioService = new PortfolioService(portfolioRepository);
    }

    @Test
    void shouldReturnPortfolioWhenIdExists() {
        Portfolio portfolio = new Portfolio(
                "Carteira Principal",
                "Carteira de longo prazo"
        );

        when(portfolioRepository.findById(1L))
                .thenReturn(Optional.of(portfolio));

        PortfolioResponse response = portfolioService.findById(1L);

        assertEquals("Carteira Principal", response.name());
        assertEquals("Carteira de longo prazo", response.description());
    }

    @Test
    void shouldThrowExceptionWhenPortfolioDoesNotExist() {
        when(portfolioRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                ()-> portfolioService.findById(999L)
        );
    }

    @Test
    void shouldReturnAllPortfolios() {

        Portfolio portfolio1 = new Portfolio(
                "Carteira Principal",
                "Carteira de longo prazo"
        );

        Portfolio portfolio2 = new Portfolio(
                "Carteira Aposentadoria",
                "Carteira voltada para aposentadoria"
        );

        when(portfolioRepository.findAll())
                .thenReturn(List.of(portfolio1, portfolio2));

        List<PortfolioResponse> response = portfolioService.findAll();

        assertEquals(2, response.size());
        assertEquals("Carteira Principal", response.get(0).name());
        assertEquals("Carteira Aposentadoria", response.get(1).name());
    }

    @Test
    void shouldCreatePortfolio() {

        CreatePortfolioRequest request = new CreatePortfolioRequest(
                "Carteira Principal",
                "Carteira de longo prazo"
        );

        Portfolio savedPortfolio = new Portfolio(
                "Carteira Principal",
                "Carteira de longo prazo"
        );

        when(portfolioRepository.save(any(Portfolio.class)))
                .thenReturn(savedPortfolio);

        PortfolioResponse response = portfolioService.create(request);

        assertEquals("Carteira Principal", response.name());
        assertEquals("Carteira de longo prazo", response.description());
    }
}
