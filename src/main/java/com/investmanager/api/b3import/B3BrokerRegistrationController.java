package com.investmanager.api.b3import;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imports/b3")
public class B3BrokerRegistrationController {
    private final B3BrokerRegistrationService registration;

    public B3BrokerRegistrationController(B3BrokerRegistrationService registration) {
        this.registration = registration;
    }

    public record RegistrationRequest(
            @NotBlank String institution,
            @NotBlank @Size(max = 100) String brokerName) {}

    @PostMapping("/broker-registration")
    public B3BrokerRegistrationService.RegistrationResponse register(
            @Valid @RequestBody RegistrationRequest request) {
        return registration.register(request.institution(), request.brokerName());
    }
}
