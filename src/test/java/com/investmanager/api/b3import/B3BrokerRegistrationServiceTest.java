package com.investmanager.api.b3import;

import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class B3BrokerRegistrationServiceTest {
    private final BrokerRepository brokers = mock(BrokerRepository.class);
    private final B3BrokerAliasService aliases = mock(B3BrokerAliasService.class);
    private final B3BrokerRegistrationService service = new B3BrokerRegistrationService(brokers, aliases);

    @Test
    void createsBrokerAndConfirmsAlias() {
        when(brokers.findAll()).thenReturn(List.of());
        when(brokers.saveAndFlush(any(Broker.class))).thenAnswer(invocation -> {
            Broker broker = invocation.getArgument(0);
            // A entidade so permite atribuir ID via JPA; use uma entidade mock como retorno.
            Broker saved = mock(Broker.class);
            when(saved.getId()).thenReturn(9L);
            when(saved.getName()).thenReturn(broker.getName());
            return saved;
        });
        var response = service.register(" BTG ", " BTG Pactual ");
        assertEquals("BTG", response.institution());
        assertEquals(9L, response.brokerId());
        assertEquals("BTG Pactual", response.brokerName());
        verify(aliases).confirm("BTG", 9L);
    }

    @Test
    void refusesExistingBrokerWithoutCreatingAlias() {
        Broker existing = new Broker();
        existing.setName("BTG Pactual");
        when(brokers.findAll()).thenReturn(List.of(existing));
        assertThrows(ResponseStatusException.class, () -> service.register("BTG", "btg  pactual"));
        verify(brokers, never()).saveAndFlush(any());
        verifyNoInteractions(aliases);
    }

    @Test
    void rejectsEmptyNames() {
        assertThrows(ResponseStatusException.class, () -> service.register(" ", "Banco Novo"));
        assertThrows(ResponseStatusException.class, () -> service.register("INTER", " "));
        verifyNoInteractions(brokers, aliases);
    }
}
