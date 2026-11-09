package com.investmanager.api.b3import;

import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Correspondencias explicitamente confirmadas pelo usuario entre nomes B3 e corretoras.
 * Nunca altera as corretoras, os ativos nem as transacoes.
 */
@Service
public class B3BrokerAliasService {
    private final JdbcTemplate jdbc;
    private final BrokerRepository brokers;

    public B3BrokerAliasService(JdbcTemplate jdbc, BrokerRepository brokers) {
        this.jdbc = jdbc;
        this.brokers = brokers;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<Long> lookup(String institution) {
        String normalized = normalize(institution);
        if (normalized.isBlank()) return Optional.empty();
        List<Long> ids = jdbc.queryForList(
                "select broker_id from b3_broker_aliases where normalized_name = ?",
                Long.class, normalized);
        if (ids.size() > 1) throw new IllegalStateException("Associacao de corretora duplicada");
        return ids.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<BrokerOption> options() {
        return brokers.findAll().stream()
                .sorted(Comparator.comparing(Broker::getName, String.CASE_INSENSITIVE_ORDER))
                .map(b -> new BrokerOption(b.getId(), b.getName()))
                .toList();
    }

    @Transactional
    public AliasResponse confirm(String institution, Long brokerId) {
        String normalized = normalize(institution);
        if (normalized.isBlank() || normalized.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nome da instituicao deve ter de 1 a 100 caracteres");
        }
        if (brokerId == null || brokerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corretora invalida");
        }
        Broker selected = brokers.findById(brokerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Corretora selecionada nao existe"));

        // Um apelido nunca pode sobrepor o nome exato de outra corretora.
        List<Long> exact = brokers.findAll().stream()
                .filter(b -> normalize(b.getName()).equals(normalized))
                .map(Broker::getId).distinct().toList();
        if (exact.size() > 1 || (!exact.isEmpty() && !exact.get(0).equals(brokerId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nome ja identifica outra corretora cadastrada");
        }

        // A constraint UNIQUE torna a gravacao segura contra concorrencia.
        jdbc.update("""
                insert into b3_broker_aliases(normalized_name, broker_id)
                values (?, ?) on conflict (normalized_name) do nothing
                """, normalized, brokerId);
        Long effectiveId = lookup(normalized).orElseThrow(
                () -> new IllegalStateException("Associacao nao encontrada apos gravacao"));
        if (!brokerId.equals(effectiveId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Instituicao ja associada a outra corretora; nenhuma substituicao foi feita");
        }
        return new AliasResponse(normalized, selected.getId(), selected.getName());
    }

    public record BrokerOption(Long id, String name) {}
    public record AliasResponse(String institution, Long brokerId, String brokerName) {}
}
