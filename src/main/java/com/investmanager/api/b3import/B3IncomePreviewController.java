package com.investmanager.api.b3import;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports/b3/incomes")
public class B3IncomePreviewController {
    private final B3IncomePreviewService service;
    private final B3IncomeReconciliationService reconciliation;
    private final B3IncomeCommitService commits;
    private final tools.jackson.databind.ObjectMapper mapper;

    public B3IncomePreviewController(B3IncomePreviewService service, B3IncomeReconciliationService reconciliation, B3IncomeCommitService commits,
            tools.jackson.databind.ObjectMapper mapper) {
        this.service = service;
        this.reconciliation = reconciliation;
        this.commits = commits;
        this.mapper = mapper;
    }

    /** Esta rota nunca persiste proventos; exige conciliação posterior. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<B3IncomePreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.preview(file));
    }
    /** Apenas conciliação: não cria nem modifica proventos. */
    @PostMapping(value = "/reconcile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3IncomeReconciliationResponse reconcile(@RequestParam("file") MultipartFile file,
            @RequestParam("portfolioId") Long portfolioId, Authentication authentication) {
        return reconciliation.reconcile(file, portfolioId, Long.valueOf(authentication.getName()));
    }
    /** Releitura e validação integral do arquivo dentro de uma transação única. */
    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3IncomeCommitResponse commit(@RequestParam("file") MultipartFile file,
            @RequestParam("portfolioId") Long portfolioId,
            @RequestParam("decisions") String decisions,
            Authentication authentication) {
        B3IncomeCommitRequest request;
        try { request = mapper.readValue(decisions, B3IncomeCommitRequest.class); }
        catch (tools.jackson.core.JacksonException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Confirmação inválida.");
        }
        return commits.commit(file, portfolioId, Long.valueOf(authentication.getName()), request);
    }
}
