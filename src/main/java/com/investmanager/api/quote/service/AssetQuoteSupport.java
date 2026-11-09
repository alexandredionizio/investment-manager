package com.investmanager.api.quote.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.quote.exception.UnsupportedAssetQuoteException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AssetQuoteSupport {
    private static final Set<AssetType> SUPPORTED_TYPES = Set.of(AssetType.STOCK, AssetType.FII, AssetType.ETF);
    private final AssetRepository assets;

    public AssetQuoteSupport(AssetRepository assets) {
        this.assets = assets;
    }

    public String requireSupported(String symbol) {
        String normalized = symbol == null ? "" : symbol.strip().toUpperCase(Locale.ROOT);
        List<Asset> matches = assets.findByTickerIgnoreCase(normalized);
        if (matches.isEmpty()) {
            throw new UnsupportedAssetQuoteException(normalized,
                    "cadastre o ativo com sua classe e bolsa antes de consultar a cotação.");
        }
        // A ticker alone cannot disambiguate conflicting asset registrations.
        for (Asset asset : matches) {
            if (asset.getType() == null || !SUPPORTED_TYPES.contains(asset.getType())) {
                throw new UnsupportedAssetQuoteException(normalized,
                        "a classe " + asset.getType() + " não possui fonte de preço implementada.");
            }
            if (asset.getExchange() == null || !"B3".equalsIgnoreCase(asset.getExchange().strip())) {
                throw new UnsupportedAssetQuoteException(normalized,
                        "somente ativos identificados como negociados na B3 possuem cotação implementada.");
            }
        }
        if (matches.stream().map(Asset::getType).distinct().count() != 1) {
            throw new UnsupportedAssetQuoteException(normalized, "há cadastros com classes diferentes para o mesmo ticker.");
        }
        return normalized;
    }
}
