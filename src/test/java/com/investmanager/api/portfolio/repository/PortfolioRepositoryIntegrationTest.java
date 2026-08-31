package com.investmanager.api.portfolio.repository;

import com.investmanager.api.portfolio.Portfolio;
import net.bytebuddy.utility.dispatcher.JavaDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Testcontainers
class PortfolioRepositoryIntegrationTest {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void shouldSaveAndFindPortfolio() {

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira criada no teste de integração"
        );

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        Optional<Portfolio> foundPortfolio =
                portfolioRepository.findById(savedPortfolio.getId());

        assertTrue(foundPortfolio.isPresent());
        assertEquals("Carteira Teste", foundPortfolio.get().getName());
        assertEquals(
                "Carteira criada no teste de integração",
                foundPortfolio.get().getDescription()
        );
    }
}
