package com.investmanager.api.b3import;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports/b3")
public class B3ReconciliationController {
    private final B3ReconciliationService service;

    public B3ReconciliationController(B3ReconciliationService service) {
        this.service = service;
    }

    /** Apenas análise: nenhum registro é persistido. */
    @PostMapping(value = "/reconcile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3ReconciliationResponse reconcile(@RequestParam("file") MultipartFile file,
                                               @RequestParam("portfolioId") Long portfolioId,
                                               Authentication authentication) {
        return service.reconcile(file, portfolioId, Long.valueOf(authentication.getName()));
    }
}
