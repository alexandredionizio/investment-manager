package com.investmanager.api.b3import;

import com.investmanager.api.asset.discovery.InstrumentEvidence;
import com.investmanager.api.asset.discovery.InstrumentIdentityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transação pequena: apenas cadastro/identificadores; nenhuma negociação ou posição. */
@Service
public class B3AssetRegistrationWriter {
    private final InstrumentIdentityService identities;
    public B3AssetRegistrationWriter(InstrumentIdentityService identities) { this.identities = identities; }

    @Transactional
    public B3AssetRegistrationService.RegistrationResponse register(InstrumentEvidence evidence) {
        var existing = identities.resolve(evidence);
        var asset = identities.registerConfirmed(evidence);
        return new B3AssetRegistrationService.RegistrationResponse(evidence.ticker(), asset.getId(), existing.isEmpty());
    }
}
