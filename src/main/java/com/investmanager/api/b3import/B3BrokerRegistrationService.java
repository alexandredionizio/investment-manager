package com.investmanager.api.b3import;

import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** Cadastra corretora e vincula um nome da B3 em uma unica transacao. */
@Service
public class B3BrokerRegistrationService {
    private final BrokerRepository brokers;
    private final B3BrokerAliasService aliases;

    public B3BrokerRegistrationService(BrokerRepository brokers, B3BrokerAliasService aliases) {
        this.brokers = brokers;
        this.aliases = aliases;
    }

    @Transactional
    public RegistrationResponse register(String institution, String brokerName) {
        String sourceName = clean(institution);
        String normalizedName = clean(brokerName);
        if (sourceName.isEmpty() || normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instituicao B3 e nome da corretora sao obrigatorios; nome com no maximo 100 caracteres.");
        }

        // Evita usar nomes semelhantes como equivalentes automaticamente.
        // Se a corretora ja existir, a tela deve usar a associacao existente.
        boolean alreadyRegistered = brokers.findAll().stream()
                .anyMatch(b -> normalize(b.getName()).equals(normalize(normalizedName)));
        if (alreadyRegistered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Corretora ja cadastrada. Selecione-a na lista de associacoes.");
        }

        Broker broker = new Broker();
        broker.setName(normalizedName);
        // Forca a violacao da restricao UNIQUE a aparecer dentro desta transacao.
        // Excecoes da associacao fazem rollback tanto da corretora quanto do alias.
        Broker saved;
        try {
            saved = brokers.saveAndFlush(broker);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Corretora ja cadastrada. Selecione-a na lista de associacoes.", e);
        }
        aliases.confirm(sourceName, saved.getId());
        return new RegistrationResponse(sourceName, saved.getId(), saved.getName());
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private static String normalize(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    public record RegistrationResponse(String institution, Long brokerId, String brokerName) {}
}
