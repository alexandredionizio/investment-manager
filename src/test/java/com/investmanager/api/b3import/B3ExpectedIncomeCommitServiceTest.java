package com.investmanager.api.b3import;

import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class B3ExpectedIncomeCommitServiceTest {
    @Test void requiresExplicitConfirmationAndSelectionBeforeAnyWrite() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new B3ExpectedIncomeCommitService(mock(B3ExpectedIncomePreviewService.class),
                mock(B3ExpectedIncomeReconciliationService.class), mock(PortfolioRepository.class), jdbc);
        var file = new MockMultipartFile("file", "eventos.xlsx", "application/octet-stream", new byte[] {1});
        assertThrows(ResponseStatusException.class,
                () -> service.commit(file, 1L, 2L, new B3ExpectedIncomeCommitRequest(false,
                        List.of(new B3ExpectedIncomeCommitRequest.Entry(2)))));
        assertThrows(ResponseStatusException.class,
                () -> service.commit(file, 1L, 2L, new B3ExpectedIncomeCommitRequest(true, List.of())));
        verifyNoInteractions(jdbc);
    }
    @Test void fingerprintIsIndependentOfDecimalScaleButSpecificToPortfolio() {
        var a = new B3ExpectedIncomePreviewRow(2, "PETR4", "Dividendo", "DIVIDEND",
                LocalDate.of(2026,12,1), "CORRETORA", "Conta 1", new BigDecimal("100.00"),
                new BigDecimal("0.50"), new BigDecimal("40.00"), List.of(), List.of());
        var b = new B3ExpectedIncomePreviewRow(3, "PETR4", "Dividendo", "DIVIDEND",
                LocalDate.of(2026,12,1), "CORRETORA", "Conta 1", new BigDecimal("100"),
                new BigDecimal("0.5"), new BigDecimal("40.0"), List.of(), List.of());
        var f1 = B3ExpectedIncomeReconciliationService.fingerprint(1L, 5L, a);
        assertEquals(f1, B3ExpectedIncomeReconciliationService.fingerprint(1L, 5L, b));
        assertNotEquals(f1, B3ExpectedIncomeReconciliationService.fingerprint(2L, 5L, a));
    }
}
