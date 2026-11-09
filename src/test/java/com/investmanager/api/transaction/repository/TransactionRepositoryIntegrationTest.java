package com.investmanager.api.transaction.repository;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
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
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFilterByPortfolioAndAssetAndOrderByDateThenId() {
        User user = userRepository.save(new User("Usuario Teste", "historico@email.com", "senha123"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio("Carteira A", "Histórico", user));
        Portfolio otherPortfolio = portfolioRepository.save(new Portfolio("Carteira B", "Isolamento", user));
        Asset asset = saveAsset("ITUB4");
        Asset otherAsset = saveAsset("VALE3");

        Transaction latest = saveTransaction(portfolio, asset, TransactionType.BUY, 20);
        Transaction sameDayFirst = saveTransaction(portfolio, asset, TransactionType.SELL, 10);
        Transaction earliest = saveTransaction(portfolio, asset, TransactionType.BUY, 1);
        Transaction sameDaySecond = saveTransaction(portfolio, asset, TransactionType.BUY, 10);
        Transaction differentAsset = saveTransaction(portfolio, otherAsset, TransactionType.BUY, 1);
        Transaction differentPortfolio = saveTransaction(otherPortfolio, asset, TransactionType.BUY, 1);

        List<Transaction> history = transactionRepository
                .findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(portfolio.getId(), asset.getId());

        assertEquals(List.of(earliest.getId(), sameDayFirst.getId(), sameDaySecond.getId(), latest.getId()),
                history.stream().map(Transaction::getId).toList());
        assertEquals(List.of(differentAsset.getId()), transactionRepository
                .findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(portfolio.getId(), otherAsset.getId())
                .stream().map(Transaction::getId).toList());
        assertEquals(List.of(differentPortfolio.getId()), transactionRepository
                .findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(otherPortfolio.getId(), asset.getId())
                .stream().map(Transaction::getId).toList());
        assertTrue(transactionRepository
                .findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(otherPortfolio.getId(), otherAsset.getId())
                .isEmpty());
    }

    private Asset saveAsset(String ticker) {
        Asset asset = new Asset();
        asset.setTicker(ticker);
        asset.setName(ticker);
        asset.setType(AssetType.STOCK);
        return assetRepository.saveAndFlush(asset);
    }

    private Transaction saveTransaction(Portfolio portfolio, Asset asset, TransactionType type, int day) {
        return transactionRepository.saveAndFlush(new Transaction(portfolio, asset, type,
                new BigDecimal("100"), BigDecimal.ONE, LocalDate.of(2026, 9, day)));
    }

    @Test
    void shouldSaveAndFindTransaction() {

        User user = userRepository.save(
                new User(
                        "Usuario Teste",
                        "usuario1@email.com",
                        "senha123"
                )
        );

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira criada no teste de integração",
                user
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

        assertEquals(
                TransactionType.BUY,
                found.getType()
        );

        assertEquals(
                new BigDecimal("100"),
                found.getQuantity()
        );

        assertEquals(
                new BigDecimal("35.50"),
                found.getUnitPrice()
        );

        assertEquals(
                "Carteira Teste",
                found.getPortfolio().getName()
        );

        assertEquals(
                "BBAS3",
                found.getAsset().getTicker()
        );
    }

    @Test
    void shouldFindTransactionsByPortfolioId() {

        User user = userRepository.save(
                new User(
                        "Usuario Teste",
                        "usuario2@email.com",
                        "senha123"
                )
        );

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira para testar consulta",
                user
        );

        Portfolio savedPortfolio =
                portfolioRepository.save(portfolio);

        Asset asset = new Asset();
        asset.setTicker("PETR4");
        asset.setName("Petrobras");
        asset.setType(AssetType.STOCK);
        asset.setSector("Petróleo");
        asset.setExchange("B3");

        Asset savedAsset =
                assetRepository.save(asset);

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
                transactionRepository
                        .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                                savedPortfolio.getId()
                        );

        assertEquals(1, transactions.size());

        assertEquals(
                "PETR4",
                transactions.getFirst().getAsset().getTicker()
        );
    }
}
