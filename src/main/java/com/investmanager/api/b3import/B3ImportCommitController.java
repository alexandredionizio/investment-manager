package com.investmanager.api.b3import;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/imports/b3")
public class B3ImportCommitController {
    private final B3ImportCommitService service;
    private final com.investmanager.api.transaction.service.B3TransactionReversalService reversals;

    public B3ImportCommitController(B3ImportCommitService service,
             com.investmanager.api.transaction.service.B3TransactionReversalService reversals) {
        this.service = service; this.reversals = reversals; }

    /** Consulta histórico restrito ao proprietário da carteira. */
    @GetMapping("/batches")
    public List<B3ImportBatchSummary> batches(@RequestParam("portfolioId") Long portfolioId,
                                               Authentication authentication) {
        return service.batches(portfolioId, Long.valueOf(authentication.getName()));
    }

    public record ReversalRequest(String reason, boolean confirmed) {}

    @GetMapping("/linked-transactions")
    public List<Long> linkedTransactions(@RequestParam Long portfolioId, Authentication authentication) {
        return reversals.importedTransactionIds(portfolioId, Long.valueOf(authentication.getName()));
    }

    @GetMapping("/reversals")
    public List<com.investmanager.api.transaction.service.B3TransactionReversalService.Reversal> reversals(
            @RequestParam Long portfolioId, Authentication authentication) {
        return reversals.reversals(portfolioId, Long.valueOf(authentication.getName()));
    }

    @PostMapping("/reversals/{transactionId}")
    public com.investmanager.api.transaction.service.B3TransactionReversalService.Reversal reverse(
            @PathVariable Long transactionId, @RequestBody ReversalRequest request, Authentication authentication) {
        return reversals.reverse(transactionId, Long.valueOf(authentication.getName()),
                request.reason(), request.confirmed());
    }

    /** A gravação requer consentimento explícito e revalida o lote dentro de uma única transação. */
    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3ImportCommitResponse commit(@RequestParam("file") MultipartFile file,
                                         @RequestParam("portfolioId") Long portfolioId,
                                         @RequestParam("confirmed") boolean confirmed,
                                         Authentication authentication) {
        return service.commit(file, portfolioId, Long.valueOf(authentication.getName()), confirmed);
    }
}
