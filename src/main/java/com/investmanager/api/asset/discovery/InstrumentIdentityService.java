package com.investmanager.api.asset.discovery;
import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.transaction.concurrency.InstrumentIdentityLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;

/** Shared identity boundary for purchases and future trusted import adapters.
 * Never merges existing Asset rows or rewrites historical financial records. */
@Service
public class InstrumentIdentityService {
    public record Identifier(String kind, String value, String exchange, String source, String observedName, LocalDate effectiveDate) {}
    private final AssetRepository assets;
    private final JdbcTemplate jdbc;
    private final InstrumentIdentityLock locks;
    public InstrumentIdentityService(AssetRepository assets, JdbcTemplate jdbc, InstrumentIdentityLock locks) {
        this.assets = assets; this.jdbc = jdbc; this.locks = locks;
    }
    public Optional<Asset> resolve(InstrumentEvidence evidence) {
        Set<Long> candidates = new HashSet<>();
        for (String ticker : tickers(evidence)) {
            candidates.addAll(ids(evidence.exchange(), "TICKER", ticker));
            for (Asset asset : assets.findByNormalizedTicker(ticker)) {
                String exchange = InstrumentEvidence.normalize(asset.getExchange());
                // Legacy null exchange is reusable only with an exact ticker and coherent class.
                if (exchange == null || exchange.isBlank() || exchange.equals(evidence.exchange())) candidates.add(asset.getId());
            }
        }
        if (evidence.isin() != null) candidates.addAll(ids(evidence.exchange(), "ISIN", evidence.isin()));
        if (candidates.size() > 1) throw InstrumentException.ambiguous();
        if (candidates.isEmpty()) return Optional.empty();
        Asset asset = assets.findById(candidates.iterator().next()).orElseThrow(InstrumentException::ambiguous);
        if (asset.getType() != evidence.type()) throw InstrumentException.ambiguous();
        if (evidence.isin() != null) {
            List<String> known = jdbc.queryForList("select value from asset_identifiers where asset_id = ? and kind = 'ISIN'", String.class, asset.getId());
            if (known.stream().anyMatch(value -> !value.equals(evidence.isin()))) throw InstrumentException.ambiguous();
        }
        return Optional.of(asset);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Asset registerConfirmed(InstrumentEvidence evidence) {
        List<String> keys = new ArrayList<>(tickers(evidence).stream().map(t -> evidence.exchange() + ":TICKER:" + t).toList());
        if (evidence.isin() != null) keys.add(evidence.exchange() + ":ISIN:" + evidence.isin());
        locks.acquire(keys);
        Asset asset = resolve(evidence).orElseGet(() -> {
            Asset created = new Asset(); created.setTicker(evidence.ticker()); created.setName(evidence.name());
            created.setType(evidence.type()); created.setExchange(evidence.exchange());
            return assets.saveAndFlush(created);
        });
        add(asset, evidence, "TICKER", evidence.ticker(), null);
        for (InstrumentEvidence.Alias alias : evidence.aliases()) add(asset, evidence, "TICKER", InstrumentEvidence.normalize(alias.ticker()), alias.effectiveDate());
        if (evidence.isin() != null) add(asset, evidence, "ISIN", evidence.isin(), null);
        return asset;
    }
    public List<Identifier> identifiers(Long assetId) {
        return jdbc.query("select kind, value, exchange, source, observed_name, effective_date from asset_identifiers where asset_id = ? order by kind, value",
                (row, index) -> new Identifier(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getObject(6, LocalDate.class)), assetId);
    }
    private Set<String> tickers(InstrumentEvidence evidence) {
        Set<String> tickers = new TreeSet<>(); tickers.add(evidence.ticker());
        evidence.aliases().forEach(alias -> tickers.add(InstrumentEvidence.normalize(alias.ticker())));
        return tickers;
    }
    private List<Long> ids(String exchange, String kind, String value) {
        return jdbc.queryForList("select asset_id from asset_identifiers where exchange = ? and kind = ? and value = ?", Long.class, exchange, kind, value);
    }
    private void add(Asset asset, InstrumentEvidence evidence, String kind, String value, LocalDate effectiveDate) {
        jdbc.update("""
                insert into asset_identifiers(exchange, kind, value, asset_id, source, observed_name, effective_date)
                values (?, ?, ?, ?, ?, ?, ?) on conflict (exchange, kind, value) do nothing
                """, evidence.exchange(), kind, value, asset.getId(), evidence.source(), evidence.name(), effectiveDate);
        if (!ids(evidence.exchange(), kind, value).equals(List.of(asset.getId()))) throw InstrumentException.ambiguous();
    }
}
