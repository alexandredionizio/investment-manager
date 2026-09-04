package com.investmanager.api.income.repository;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
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
class IncomeRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Test
    void shouldSaveAndFindIncome() {

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira para teste de proventos"
        );

        Portfolio savedPortfolio =
                portfolioRepository.save(portfolio);

        Asset asset = new Asset();
        asset.setTicker("ITUB4");
        asset.setName("Itaú Unibanco");
        asset.setType(AssetType.STOCK);
        asset.setSector("Financeiro");
        asset.setExchange("B3");

        Asset savedAsset =
                assetRepository.save(asset);

        Income income = new Income(
                savedPortfolio,
                savedAsset,
                IncomeType.DIVIDEND,
                new BigDecimal("0.50"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 3)
        );

        Income savedIncome =
                incomeRepository.save(income);

        Optional<Income> foundIncome =
                incomeRepository.findById(savedIncome.getId());

        assertTrue(foundIncome.isPresent());

        Income found = foundIncome.get();

        assertEquals(IncomeType.DIVIDEND, found.getType());
        assertEquals(
                0,
                new BigDecimal("0.50")
                        .compareTo(found.getAmountPerUnit())
        );
        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(found.getQuantity())
        );
        assertEquals(
                LocalDate.of(2026, 9, 3),
                found.getPaymentDate()
        );

        assertEquals(
                "Carteira Teste",
                found.getPortfolio().getName()
        );

        assertEquals(
                "ITUB4",
                found.getAsset().getTicker()
        );
    }

    @Test
    void shouldFindIncomesByPortfolioIdOrderedByPaymentDateAndId() {

        Portfolio portfolio = new Portfolio(
                "Carteira Teste",
                "Carteira para testar ordenação de proventos"
        );

        Portfolio savedPortfolio =
                portfolioRepository.save(portfolio);

        Asset asset = new Asset();
        asset.setTicker("ITUB4");
        asset.setName("Itaú Unibanco");
        asset.setType(AssetType.STOCK);
        asset.setSector("Financeiro");
        asset.setExchange("B3");

        Asset savedAsset =
                assetRepository.save(asset);

        Income income1 = new Income(
                savedPortfolio,
                savedAsset,
                IncomeType.DIVIDEND,
                new BigDecimal("0.30"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 10)
        );

        Income income2 = new Income(
                savedPortfolio,
                savedAsset,
                IncomeType.JCP,
                new BigDecimal("0.20"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 5)
        );

        Income income3 = new Income(
                savedPortfolio,
                savedAsset,
                IncomeType.DIVIDEND,
                new BigDecimal("0.10"),
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 10)
        );

        incomeRepository.save(income1);
        incomeRepository.save(income2);
        incomeRepository.save(income3);

        List<Income> incomes =
                incomeRepository
                        .findByPortfolioIdOrderByPaymentDateAscIdAsc(
                                savedPortfolio.getId()
                        );

        assertEquals(3, incomes.size());

        assertEquals(
                LocalDate.of(2026, 9, 5),
                incomes.get(0).getPaymentDate()
        );

        assertEquals(
                LocalDate.of(2026, 9, 10),
                incomes.get(1).getPaymentDate()
        );

        assertEquals(
                LocalDate.of(2026, 9, 10),
                incomes.get(2).getPaymentDate()
        );

        assertTrue(
                incomes.get(1).getId() < incomes.get(2).getId()
        );
    }
}