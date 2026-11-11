package com.investmanager.api.b3import;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/imports/b3/expected-incomes")
public class B3ExpectedIncomePreviewController {
    private final B3ExpectedIncomePreviewService previews;
    private final B3ExpectedIncomeReconciliationService reconciliation;
    private final B3ExpectedIncomeCommitService commits;
    private final tools.jackson.databind.ObjectMapper mapper;
    public B3ExpectedIncomePreviewController(B3ExpectedIncomePreviewService previews,
            B3ExpectedIncomeReconciliationService reconciliation,
            B3ExpectedIncomeCommitService commits, tools.jackson.databind.ObjectMapper mapper) {
        this.previews = previews; this.reconciliation = reconciliation; this.commits = commits; this.mapper = mapper;
    }
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3ExpectedIncomePreviewResponse preview(@RequestParam("file") MultipartFile file) {
        return previews.preview(file);
    }
    @PostMapping(value = "/reconcile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3ExpectedIncomeReconciliationResponse reconcile(@RequestParam("file") MultipartFile file,
            @RequestParam("portfolioId") Long portfolioId, Authentication auth) {
        return reconciliation.reconcile(file, portfolioId, Long.valueOf(auth.getName()));
    }
    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3ExpectedIncomeCommitResponse commit(@RequestParam("file") MultipartFile file,
            @RequestParam("portfolioId") Long portfolioId, @RequestParam("decisions") String decisions,
            Authentication auth) {
        B3ExpectedIncomeCommitRequest request;
        try { request = mapper.readValue(decisions, B3ExpectedIncomeCommitRequest.class); }
        catch (tools.jackson.core.JacksonException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmação inválida."); }
        return commits.commit(file, portfolioId, Long.valueOf(auth.getName()), request);
    }
}
