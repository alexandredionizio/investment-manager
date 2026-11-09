package com.investmanager.api.b3import;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports/b3")
public class B3PreviewController {
    private final B3SpreadsheetPreviewService service;
    public B3PreviewController(B3SpreadsheetPreviewService service) { this.service = service; }

    /** Apenas pré-visualização: não cria ativos, transações nem altera saldos. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<B3PreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.preview(file));
    }
}
