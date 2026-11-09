package com.investmanager.api.b3import;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Consulta de correspondências. Não registra transações nem modifica cadastros. */
@RestController
@RequestMapping("/api/imports/b3")
public class B3MatchingController {
    private final B3MatchingService matchingService;

    public B3MatchingController(B3MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping(value = "/matches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public B3MatchResponse match(@RequestParam("file") MultipartFile file) {
        return matchingService.match(file);
    }
}
