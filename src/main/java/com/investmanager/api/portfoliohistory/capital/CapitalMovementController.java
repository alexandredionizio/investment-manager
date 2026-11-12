package com.investmanager.api.portfoliohistory.capital;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/capital-movements")
public class CapitalMovementController {
    private final CapitalMovementService service;
    public CapitalMovementController(CapitalMovementService service) { this.service = service; }

    @GetMapping
    public List<CapitalMovementResponse> list(@PathVariable Long portfolioId, Authentication authentication) {
        return service.list(portfolioId, Long.valueOf(authentication.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CapitalMovementResponse create(@PathVariable Long portfolioId,
                                          @Valid @RequestBody CapitalMovementRequest request,
                                          Authentication authentication) {
        return service.create(portfolioId, Long.valueOf(authentication.getName()), request);
    }
}
