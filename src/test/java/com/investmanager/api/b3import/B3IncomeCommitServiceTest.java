package com.investmanager.api.b3import;

import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class B3IncomeCommitServiceTest {
    private final B3IncomePreviewService previews = mock(B3IncomePreviewService.class);
    private final B3IncomeReconciliationService reconciles = mock(B3IncomeReconciliationService.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final IncomeRepository incomes = mock(IncomeRepository.class);
    private final PortfolioAssetLockManager locks = mock(PortfolioAssetLockManager.class);
    private final B3IncomeCommitService service = new B3IncomeCommitService(previews, reconciles, portfolios, assets, incomes, locks);
    private final MockMultipartFile file = new MockMultipartFile("file", "x.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

    @Test void cannotCommitWithoutConfirmation() {
        assertThrows(ResponseStatusException.class, () -> service.commit(file, 1L, 1L,
                new B3IncomeCommitRequest(false, List.of(new B3IncomeCommitRequest.Entry(2)))));
        verifyNoInteractions(previews, reconciles, incomes, locks);
    }
    @Test void cannotCommitEmptySelection() {
        assertThrows(ResponseStatusException.class, () -> service.commit(file, 1L, 1L,
                new B3IncomeCommitRequest(true, List.of())));
        verifyNoInteractions(previews, reconciles, incomes, locks);
    }
    @Test void cannotCommitUnknownPortfolio() {
        assertThrows(ResponseStatusException.class, () -> service.commit(file, 1L, 99L,
                new B3IncomeCommitRequest(true, List.of(new B3IncomeCommitRequest.Entry(2)))));
        verifyNoInteractions(previews, reconciles, incomes, locks);
    }
}
