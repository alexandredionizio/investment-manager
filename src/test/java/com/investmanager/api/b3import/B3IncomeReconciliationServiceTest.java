package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class B3IncomeReconciliationServiceTest {
    private final B3IncomePreviewService previews = mock(B3IncomePreviewService.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final IncomeRepository incomes = mock(IncomeRepository.class);
    private final B3IncomeReconciliationService service = new B3IncomeReconciliationService(previews, portfolios, assets, incomes);
    private final MockMultipartFile file = new MockMultipartFile("file", "movimentacao.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1});

    private B3IncomePreviewRow row(String product) {
        return new B3IncomePreviewRow(2, LocalDate.of(2026, 10, 15), "Juros Sobre Capital Próprio",
                product, "CORRETORA", "JCP", new BigDecimal("100"), new BigDecimal("0.20"),
                new BigDecimal("17.00"), List.of(), List.of());
    }

    @Test
    void refusesPortfolioOfAnotherUserBeforeReadingFile() {
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(file, 10L, 99L));
        verifyNoInteractions(previews, assets, incomes);
    }

    @Test
    void identifiesTickerButBlocksAutomaticCommitWithoutBaseDate() {
        when(portfolios.findByIdAndUserId(10L, 99L)).thenReturn(Optional.of(mock(Portfolio.class)));
        when(previews.preview(file)).thenReturn(new B3IncomePreviewResponse("movimentacao.xlsx", 1, 1, 0, List.of(row("BBDC3 - BRADESCO"))));
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(7L);
        when(assets.findByNormalizedTicker("BBDC3")).thenReturn(List.of(asset));
        var answer = service.reconcile(file, 10L, 99L);
        assertEquals(1, answer.identified());
        assertEquals("READY", answer.rows().get(0).status());
        assertTrue(answer.rows().get(0).messages().stream().anyMatch(m -> m.contains("líquido")));
        verify(incomes, never()).save(any(Income.class));
    }

    @Test
    void marksSuspectedDuplicateWithoutClaimingExactIdentity() {
        when(portfolios.findByIdAndUserId(10L, 99L)).thenReturn(Optional.of(mock(Portfolio.class)));
        when(previews.preview(file)).thenReturn(new B3IncomePreviewResponse("x.xlsx", 1, 1, 0, List.of(row("BBDC3"))));
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(7L);
        when(assets.findByNormalizedTicker("BBDC3")).thenReturn(List.of(asset));
        Income existing = mock(Income.class);
        when(existing.getPaymentDate()).thenReturn(LocalDate.of(2026, 10, 15));
        when(existing.getType()).thenReturn(IncomeType.JCP);
        when(existing.getQuantity()).thenReturn(new BigDecimal("100"));
        when(existing.getAmountPerUnit()).thenReturn(new BigDecimal("0.17"));
        when(incomes.findByPortfolioIdAndAssetIdOrderByBaseDateAscIdAsc(10L, 7L)).thenReturn(List.of(existing));
        assertEquals("POSSIBLE_DUPLICATE", service.reconcile(file, 10L, 99L).rows().get(0).status());
        verify(incomes, never()).save(any(Income.class));
    }
}
