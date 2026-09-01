package com.investmanager.api.transaction.repository;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Testcontainers
class TransactionRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postrgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Test
    void shouldSaveAndFindTransaction() {

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira criada no teste de integração"
        );

        Portfolio savedPortfolio =
                portfolioRepository.save(portfolio);

        Asset asset = new Asset();
        asset.setTicker("BBAS3");
        asset.setName("Banco do Brasil");
        asset.setType(AssetType.STOCK);
        asset.setSector("Financeiro");
        asset.setExchange("B3");

        Asset savedAsset =
                assetRepository.save(asset);

        Transaction transaction = new Transaction(
                savedPortfolio,
                savedAsset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        Optional<Transaction> foundTransaction =
                transactionRepository.findById(savedTransaction.getId());

        assertTrue(foundTransaction.isPresent());

        Transaction found = foundTransaction.get();

        assertEquals(TransactionType.BUY, found.getType());
        assertEquals(new BigDecimal("100"), found.getQuantity());
        assertEquals(new BigDecimal("35.50"), found.getUnitPrice());

        assertEquals("Carteira Teste",
                found.getPortfolio().getName());

        assertEquals("BBAS3",
                found.getAsset().getTicker());
    }

    @Test
    void shouldFindTransactionsByPortfolioId() {

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira para testar consulta"
        );

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        Asset asset = new Asset();
        asset.setTicker("PETR4");
        asset.setName("Petrobras");
        asset.setType(AssetType.STOCK);
        asset.setSector("Petróleo");
        asset.setExchange("B3");

        Asset savedAsset = assetRepository.save(asset);

        Transaction transaction = new Transaction(
                savedPortfolio,
                savedAsset,
                TransactionType.BUY,
                new BigDecimal("50"),
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1)
        );

        transactionRepository.save(transaction);

        List<Transaction> transactions =
                transactionRepository.findByPortfolioId(savedPortfolio.getId());

        assertEquals(1, transactions.size());
        assertEquals("PETR4", transactions.getFirst().getAsset().getTicker());
    }
}
