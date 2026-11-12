package com.investmanager.api.portfoliohistory.xirr;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only diagnostic endpoint; not yet part of the product dashboard. */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/xirr-preview")
public class PortfolioXirrPreviewController {
    private final PortfolioXirrPreviewService service;

    public PortfolioXirrPreviewController(PortfolioXirrPreviewService service) {
        this.service = service;
    }

    @GetMapping
    public PortfolioXirrPreviewResponse preview(@PathVariable Long portfolioId, Authentication authentication) {
        return service.preview(portfolioId, Long.valueOf(authentication.getName()));
    }
}
