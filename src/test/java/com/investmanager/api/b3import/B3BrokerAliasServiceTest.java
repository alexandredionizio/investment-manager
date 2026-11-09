package com.investmanager.api.b3import;

import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class B3BrokerAliasServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BrokerRepository brokers = mock(BrokerRepository.class);
    private final B3BrokerAliasService service = new B3BrokerAliasService(jdbc, brokers);

    @Test
    void normalizeAccentsAndSpaces() {
        assertEquals("ITAU INVESTIMENTOS", B3BrokerAliasService.normalize("  Itaú   Investimentos "));
    }

    @Test
    void registersAndReusesUserConfirmedAlias() {
        Broker broker = mock(Broker.class);
        when(broker.getId()).thenReturn(1L);
        when(broker.getName()).thenReturn("XP Investimentos");
        when(brokers.findById(1L)).thenReturn(Optional.of(broker));
        when(brokers.findAll()).thenReturn(List.of(broker));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("XP"))).thenReturn(List.of(1L));

        var result = service.confirm(" xp ", 1L);
        assertEquals("XP", result.institution());
        assertEquals(1L, result.brokerId());
        verify(jdbc).update(contains("on conflict"), eq("XP"), eq(1L));
    }

    @Test
    void rejectsConflictingAliasWithoutOverwriting() {
        Broker broker = mock(Broker.class);
        when(broker.getId()).thenReturn(1L);
        when(broker.getName()).thenReturn("XP Investimentos");
        when(brokers.findById(1L)).thenReturn(Optional.of(broker));
        when(brokers.findAll()).thenReturn(List.of(broker));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("XP"))).thenReturn(List.of(2L));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.confirm("XP", 1L));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void rejectsAliasThatShadowsOtherBrokersExactName() {
        Broker chosen = mock(Broker.class);
        Broker other = mock(Broker.class);
        when(chosen.getId()).thenReturn(1L);
        when(chosen.getName()).thenReturn("XP Investimentos");
        when(other.getId()).thenReturn(2L);
        when(other.getName()).thenReturn("Itaú");
        when(brokers.findById(1L)).thenReturn(Optional.of(chosen));
        when(brokers.findAll()).thenReturn(List.of(chosen, other));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.confirm("ITAU", 1L));
        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnknownBrokerAndInvalidInstitution() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.confirm("XP", 999L)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.confirm(" ", 1L)).getStatusCode().value());
    }
}
