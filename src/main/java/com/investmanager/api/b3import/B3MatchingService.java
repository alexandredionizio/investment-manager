package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * IM-039: identifica cadastros internos sem criar, atualizar nem fundir registros.
 * Somente nomes de corretora exatamente equivalentes após normalização são confirmados.
 * Sugestões por prefixo são sempre submetidas à revisão do usuário.
 */
@Service
public class B3MatchingService {
    private final B3SpreadsheetPreviewService previewService;
    private final AssetRepository assetRepository;
    private final BrokerRepository brokerRepository;
    private final B3BrokerAliasService aliases;

    public B3MatchingService(B3SpreadsheetPreviewService previewService,
                             AssetRepository assetRepository,
                             BrokerRepository brokerRepository,
                             B3BrokerAliasService aliases) {
        this.previewService = previewService;
        this.assetRepository = assetRepository;
        this.brokerRepository = brokerRepository;
        this.aliases = aliases;
    }

    @Transactional(readOnly = true)
    public B3MatchResponse match(MultipartFile file) {
        B3PreviewResponse preview = previewService.preview(file);
        List<Broker> brokers = brokerRepository.findAll();
        List<B3MatchResponse.Row> results = new ArrayList<>();
        int matched = 0;
        int review = 0;
        int invalid = 0;

        for (B3PreviewRow row : preview.rows()) {
            if (!row.valid()) {
                invalid++;
                results.add(new B3MatchResponse.Row(row.excelRow(), row.ticker(), row.institution(),
                        "INVALID", null, "INVALID", null, List.of(), row.errors()));
                continue;
            }

            String ticker = normalize(row.ticker());
            List<Asset> assets = ticker.isEmpty() ? List.of() :
                    assetRepository.findByNormalizedTicker(ticker);
            String assetStatus = assets.isEmpty() ? "NOT_FOUND" :
                    assets.size() == 1 ? "MATCHED" : "AMBIGUOUS";
            Long assetId = assets.size() == 1 ? assets.get(0).getId() : null;

            String institution = normalize(row.institution());
            List<Broker> exact = brokers.stream()
                    .filter(b -> Objects.equals(normalize(b.getName()), institution))
                    .toList();
            String brokerStatus = exact.isEmpty() ? "NOT_FOUND" :
                    exact.size() == 1 ? "MATCHED" : "AMBIGUOUS";
            Long brokerId = exact.size() == 1 ? exact.get(0).getId() : null;

            // Apenas associacoes ja confirmadas podem resolver um apelido automaticamente.
            // Se houver nome exato ambiguo, nunca consultar ou sobrescrever por apelido.
            if (exact.isEmpty() && !institution.isEmpty()) {
                var confirmed = aliases.lookup(institution);
                if (confirmed.isPresent()) {
                    Long id = confirmed.get();
                    if (brokers.stream().anyMatch(b -> id.equals(b.getId()))) {
                        brokerStatus = "MATCHED";
                        brokerId = id;
                    }
                }
            }

            // Approximate name matches are suggestions only; never automatically assign an ID.
            List<Broker> proposals = !"MATCHED".equals(brokerStatus) && exact.isEmpty() && !institution.isEmpty()
                    ? brokers.stream()
                        .filter(b -> {
                            String name = normalize(b.getName());
                            return name.startsWith(institution + " ") || institution.startsWith(name + " ");
                        })
                        .limit(10)
                        .toList()
                    : exact;
            List<B3MatchResponse.BrokerOption> options = proposals.stream()
                    .map(b -> new B3MatchResponse.BrokerOption(b.getId(), b.getName()))
                    .toList();

            List<String> messages = new ArrayList<>();
            if (!"MATCHED".equals(assetStatus)) messages.add("Ativo: " + assetStatus);
            if (!"MATCHED".equals(brokerStatus)) messages.add("Corretora: " + brokerStatus);
            if ("MATCHED".equals(assetStatus) && "MATCHED".equals(brokerStatus)) matched++;
            else review++;

            results.add(new B3MatchResponse.Row(row.excelRow(), row.ticker(), row.institution(),
                    assetStatus, assetId, brokerStatus, brokerId, options, List.copyOf(messages)));
        }
        return new B3MatchResponse(preview.fileName(), preview.totalRows(), matched, review,
                invalid, List.copyOf(results));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
