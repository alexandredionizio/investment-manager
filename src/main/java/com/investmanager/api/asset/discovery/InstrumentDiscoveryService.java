package com.investmanager.api.asset.discovery;
import com.investmanager.api.asset.*;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class InstrumentDiscoveryService {
    public record Candidate(String ticker, String name, AssetType type, String exchange, Long assetId, boolean requiresReview) {}
    public record SearchResult(List<Candidate> items, int page, boolean hasNextPage) {}
    private final BrapiCatalogClient catalog;
    private final InstrumentIdentityService identities;
    public InstrumentDiscoveryService(BrapiCatalogClient catalog, InstrumentIdentityService identities) { this.catalog = catalog; this.identities = identities; }
    public SearchResult search(String query, int page, int limit) {
        var result = catalog.search(query, page, limit);
        return new SearchResult(result.items().stream().map(item -> {
            try { return new Candidate(item.ticker(), item.name(), item.type(), item.exchange(), identities.resolve(item).map(Asset::getId).orElse(null), false); }
            catch (InstrumentException conflict) {
                if (conflict.status() != 409) throw conflict;
                return new Candidate(item.ticker(), item.name(), item.type(), item.exchange(), null, true);
            }
        }).toList(), result.page(), result.hasNextPage());
    }
}
