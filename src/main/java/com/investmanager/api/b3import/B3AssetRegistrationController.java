package com.investmanager.api.b3import;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/imports/b3/assets")
public class B3AssetRegistrationController {
    public record RegistrationRequest(@NotBlank String ticker) {}
    private final B3AssetRegistrationService service;
    public B3AssetRegistrationController(B3AssetRegistrationService service) { this.service = service; }

    @GetMapping("/candidate")
    public B3AssetRegistrationService.Candidate lookup(@RequestParam String ticker) { return service.lookup(ticker); }

    @PostMapping("/register")
    public B3AssetRegistrationService.RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return service.register(request.ticker());
    }
}
