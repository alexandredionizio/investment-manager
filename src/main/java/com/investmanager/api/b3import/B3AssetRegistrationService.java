package com.investmanager.api.b3import;

import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.discovery.BrapiCatalogClient;
import com.investmanager.api.asset.discovery.InstrumentEvidence;
import com.investmanager.api.asset.discovery.InstrumentException;
import com.investmanager.api.asset.discovery.InstrumentIdentityService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** IM-040: identidade confirmada pelo catálogo confiável, sem registrar negociações. */
@Service
public class B3AssetRegistrationService {
    public record Candidate(String ticker, String name, AssetType type, String exchange, Long assetId, boolean requiresReview) {}
    public record RegistrationResponse(String ticker, Long assetId, boolean created) {}

    private final BrapiCatalogClient catalog;
    private final InstrumentIdentityService identities;
    private final B3AssetRegistrationWriter writer;

    public B3AssetRegistrationService(BrapiCatalogClient catalog, InstrumentIdentityService identities, B3AssetRegistrationWriter writer) {
        this.catalog = catalog;
        this.identities = identities;
        this.writer = writer;
    }

    public Candidate lookup(String ticker) {
        var evidence = verifiedEvidence(ticker);
        var resolved = identities.resolve(evidence);
        return new Candidate(evidence.ticker(), evidence.name(), evidence.type(), evidence.exchange(),
                resolved.map(asset -> asset.getId()).orElse(null), false);
    }

    public RegistrationResponse register(String ticker) {
        var evidence = verifiedEvidence(ticker); // Nunca aceitar nome/classe/ISIN enviados pelo navegador
        return writer.register(evidence); // abre transação somente após consulta remota
    }

    private InstrumentEvidence verifiedEvidence(String value) {
        String ticker = normalizeTicker(value);
        InstrumentEvidence evidence = catalog.confirm(ticker);
        if (evidence == null || !evidence.active() || !ticker.equals(evidence.ticker())
                || !"B3".equals(evidence.exchange())
                || !List.of(AssetType.STOCK, AssetType.FII, AssetType.ETF).contains(evidence.type())) {
            throw InstrumentException.ineligible();
        }
        return evidence;
    }

    private static String normalizeTicker(String value) {
        if (value == null) throw InstrumentException.invalid();
        String ticker = value.trim().toUpperCase(Locale.ROOT);
        if (!ticker.matches("[A-Z0-9][A-Z0-9._-]{1,19}")) throw InstrumentException.invalid();
        return ticker;
    }
}
