package com.investmanager.api.portfolio.repository;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Testcontainers
class PortfolioRepositoryIntegrationTest {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private UserRepository userRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void shouldSaveAndFindPortfolioByUser() {

        User user = new User(
                "Usuario Teste",
                "usuario@email.com",
                "senha123"
        );

        User savedUser = userRepository.save(user);

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira criada no teste de integração",
                savedUser
        );

        Portfolio savedPortfolio =
                portfolioRepository.save(portfolio);

        Optional<Portfolio> foundPortfolio =
                portfolioRepository.findByIdAndUserId(
                        savedPortfolio.getId(),
                        savedUser.getId()
                );

        assertTrue(foundPortfolio.isPresent());

        assertEquals(
                "Carteira Teste",
                foundPortfolio.get().getName()
        );

        assertEquals(
                "Carteira criada no teste de integração",
                foundPortfolio.get().getDescription()
        );
    }

    @Test
    void shouldReturnOnlyPortfoliosFromUser() {

        User user1 = userRepository.save(
                new User(
                        "Usuario Um",
                        "usuario1@email.com",
                        "senha123"
                )
        );

        User user2 = userRepository.save(
                new User(
                        "Usuario Dois",
                        "usuario2@email.com",
                        "senha123"
                )
        );

        portfolioRepository.save(
                new Portfolio(
                        "Carteira Usuario 1",
                        "Carteira do primeiro usuario",
                        user1
                )
        );

        portfolioRepository.save(
                new Portfolio(
                        "Carteira Usuario 2",
                        "Carteira do segundo usuario",
                        user2
                )
        );

        List<Portfolio> portfolios =
                portfolioRepository.findAllByUserId(user1.getId());

        assertEquals(1, portfolios.size());

        assertEquals(
                "Carteira Usuario 1",
                portfolios.get(0).getName()
        );
    }
}