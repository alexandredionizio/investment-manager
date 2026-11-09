package com.investmanager.api.valuation;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
public class ValuationController {
    private final PositionValuationService valuations;
    private final ValuationContractService contracts;
    public ValuationController(PositionValuationService valuations,ValuationContractService contracts) {
        this.valuations=valuations;this.contracts=contracts;
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<com.investmanager.api.shared.exception.ErrorResponse> invalidContract(
            org.springframework.web.server.ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(
                new com.investmanager.api.shared.exception.ErrorResponse(e.getStatusCode().value(), "Contrato inválido", e.getReason()));
    }
    @GetMapping("/positions/valuations")
    public List<PositionValuationResponse> evaluate(@PathVariable Long portfolioId,Authentication authentication) {
        return valuations.evaluate(portfolioId,Long.valueOf(authentication.getName()));
    }
    @GetMapping("/assets/{assetId}/valuation-contract")
    public ValuationTerms get(@PathVariable Long portfolioId,@PathVariable Long assetId,Authentication authentication) {
        return contracts.get(portfolioId,assetId,Long.valueOf(authentication.getName()));
    }
    @PutMapping("/assets/{assetId}/valuation-contract")
    public ValuationTerms save(@PathVariable Long portfolioId,@PathVariable Long assetId,Authentication authentication,
            @Valid @RequestBody ValuationTerms terms) {
        return contracts.save(portfolioId,assetId,Long.valueOf(authentication.getName()),terms);
    }
}
