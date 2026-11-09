package com.investmanager.api.b3import;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gerencia apenas associacoes de nomes B3 aprovadas pelo usuario. */
@RestController
@RequestMapping("/api/imports/b3")
public class B3BrokerAliasController {
    private final B3BrokerAliasService aliases;

    public B3BrokerAliasController(B3BrokerAliasService aliases) {
        this.aliases = aliases;
    }

    public record ConfirmRequest(@NotBlank String institution, @NotNull @Positive Long brokerId) {}

    @GetMapping("/broker-options")
    public List<B3BrokerAliasService.BrokerOption> brokerOptions() {
        return aliases.options();
    }

    @PostMapping("/broker-aliases")
    public B3BrokerAliasService.AliasResponse confirm(@Valid @RequestBody ConfirmRequest request) {
        return aliases.confirm(request.institution(), request.brokerId());
    }
}
