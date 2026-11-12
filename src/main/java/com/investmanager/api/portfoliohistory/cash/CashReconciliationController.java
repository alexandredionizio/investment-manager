package com.investmanager.api.portfoliohistory.cash;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/cash-reconciliation")
public class CashReconciliationController {
    private final CashReconciliationService service;
    public CashReconciliationController(CashReconciliationService service) { this.service = service; }

    @GetMapping
    public CashReconciliationResponse reconcile(@PathVariable Long portfolioId,
            @RequestParam(required = false) LocalDate asOf, Authentication authentication) {
        return service.reconcile(portfolioId, Long.valueOf(authentication.getName()),
                asOf == null ? LocalDate.now() : asOf);
    }

    @PostMapping("/opening-checkpoint")
    @ResponseStatus(HttpStatus.CREATED)
    public CashReconciliationResponse declareOpening(@PathVariable Long portfolioId,
            @Valid @RequestBody CashCheckpointRequest request, Authentication authentication) {
        return service.createCheckpoint(portfolioId, Long.valueOf(authentication.getName()), request);
    }
}
