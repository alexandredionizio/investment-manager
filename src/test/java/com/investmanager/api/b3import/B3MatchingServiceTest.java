package com.investmanager.api.b3import;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class B3MatchingServiceTest {
    private final B3SpreadsheetPreviewService preview = mock(B3SpreadsheetPreviewService.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final BrokerRepository brokers = mock(BrokerRepository.class);
    private final B3BrokerAliasService aliases = mock(B3BrokerAliasService.class);
    private final B3MatchingService service = new B3MatchingService(preview, assets, brokers, aliases);

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[]{1});
    }
    private B3PreviewRow row(int number, String ticker, String institution, List<String> errors) {
        return new B3PreviewRow(number, LocalDate.of(2026, 9, 1), "Compra", "Mercado à Vista",
                "-", institution, ticker, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                errors, List.of());
    }
    private void preview(B3PreviewRow... rows) {
        when(preview.preview(any())).thenReturn(new B3PreviewResponse("test.xlsx", rows.length,
                (int) java.util.Arrays.stream(rows).filter(B3PreviewRow::valid).count(),
                (int) java.util.Arrays.stream(rows).filter(r -> !r.valid()).count(), List.of(rows)));
    }

    @Test
    void matchesOnlyExactNormalizedBrokerNameAndSingleAsset() {
        preview(row(2, "PETR4", "ITAU", List.of()));
        Broker broker = mock(Broker.class);
        when(broker.getId()).thenReturn(7L);
        when(broker.getName()).thenReturn("Itaú");
        when(brokers.findAll()).thenReturn(List.of(broker));
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(11L);
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of(asset));

        B3MatchResponse result = service.match(file());
        assertEquals(1, result.matchedRows());
        assertEquals(11L, result.rows().get(0).assetId());
        assertEquals(7L, result.rows().get(0).brokerId());
        verify(assets, never()).save(any());
        verify(brokers, never()).save(any());
    }

    @Test
    void suggestsPrefixButNeverAutoAssignsBroker() {
        preview(row(2, "PETR4", "XP", List.of()));
        Broker broker = mock(Broker.class);
        when(broker.getId()).thenReturn(8L);
        when(broker.getName()).thenReturn("XP Investimentos");
        when(brokers.findAll()).thenReturn(List.of(broker));
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals(1, result.reviewRows());
        assertNull(result.rows().get(0).brokerId());
        assertEquals("NOT_FOUND", result.rows().get(0).brokerStatus());
        assertEquals(8L, result.rows().get(0).brokerSuggestions().get(0).id());
    }

    @Test
    void confirmedAliasResolvesBrokerAcrossRowsWithoutCreatingRecords() {
        preview(row(2, "PETR4", "XP", List.of()), row(3, "PETR4", "XP", List.of()));
        Broker broker = mock(Broker.class);
        when(broker.getId()).thenReturn(8L);
        when(broker.getName()).thenReturn("XP Investimentos");
        when(brokers.findAll()).thenReturn(List.of(broker));
        when(aliases.lookup("XP")).thenReturn(java.util.Optional.of(8L));
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(11L);
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of(asset));

        B3MatchResponse result = service.match(file());
        assertEquals(2, result.matchedRows());
        assertEquals(8L, result.rows().get(0).brokerId());
        assertEquals(8L, result.rows().get(1).brokerId());
        verify(assets, never()).save(any());
        verify(brokers, never()).save(any());
    }

    @Test
    void invalidRowsDoNotTriggerAssetLookup() {
        preview(row(2, "PETR4", "XP", List.of("Data inválida")));
        B3MatchResponse result = service.match(file());
        assertEquals(1, result.invalidRows());
        verifyNoInteractions(assets);
    }

    @Test
    void ambiguousTickerMustRequireReviewWithoutAssigningAsset() {
        preview(row(2, "PETR4", "XP", List.of()));
        Asset first = mock(Asset.class);
        Asset second = mock(Asset.class);
        when(first.getId()).thenReturn(101L);
        when(second.getId()).thenReturn(102L);
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of(first, second));
        when(brokers.findAll()).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals(0, result.matchedRows());
        assertEquals(1, result.reviewRows());
        assertEquals("AMBIGUOUS", result.rows().get(0).assetStatus());
        assertNull(result.rows().get(0).assetId());
        verify(assets, never()).save(any());
    }

    @Test
    void ambiguousExactBrokerNamesMustNotBeResolvedByConfirmedAlias() {
        preview(row(2, "PETR4", "ITAU", List.of()));
        Asset asset = mock(Asset.class);
        when(asset.getId()).thenReturn(11L);
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of(asset));
        Broker first = mock(Broker.class);
        Broker second = mock(Broker.class);
        when(first.getName()).thenReturn("Itaú");
        when(second.getName()).thenReturn("ITAU");
        when(brokers.findAll()).thenReturn(List.of(first, second));

        B3MatchResponse result = service.match(file());
        assertEquals("AMBIGUOUS", result.rows().get(0).brokerStatus());
        assertNull(result.rows().get(0).brokerId());
        assertEquals(1, result.reviewRows());
        verifyNoInteractions(aliases);
        verify(brokers, never()).save(any());
    }

    @Test
    void staleBrokerAliasMustNotMatchMissingBroker() {
        preview(row(2, "PETR4", "XP", List.of()));
        when(brokers.findAll()).thenReturn(List.of());
        when(aliases.lookup("XP")).thenReturn(java.util.Optional.of(999L));
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals("NOT_FOUND", result.rows().get(0).brokerStatus());
        assertNull(result.rows().get(0).brokerId());
        assertEquals(1, result.reviewRows());
        verify(brokers, never()).save(any());
    }
    @Test
    void confirmedHistoricalB3TickerMatchesSameAssetWithoutCreatingIt() {
        preview(row(2, "ANTG3", "XP", List.of()));
        Asset historical = mock(Asset.class);
        when(historical.getId()).thenReturn(45L);
        when(assets.findByNormalizedTicker("ANTG3")).thenReturn(List.of());
        when(assets.findByConfirmedB3Ticker("ANTG3")).thenReturn(List.of(historical));
        when(brokers.findAll()).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals("MATCHED", result.rows().get(0).assetStatus());
        assertEquals(45L, result.rows().get(0).assetId());
        assertEquals(1, result.reviewRows()); // Corretora ainda precisa de revisão
        verify(assets, never()).save(any());
    }

    @Test
    void sameAssetFoundByCurrentAndHistoricalTickerIsNotAmbiguous() {
        preview(row(2, "PETR4", "XP", List.of()));
        Asset same = mock(Asset.class);
        when(same.getId()).thenReturn(45L);
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of(same));
        when(assets.findByConfirmedB3Ticker("PETR4")).thenReturn(List.of(same));
        when(brokers.findAll()).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals("MATCHED", result.rows().get(0).assetStatus());
        assertEquals(45L, result.rows().get(0).assetId());
    }

    @Test
    void tickerCollisionBetweenCurrentAssetAndConfirmedHistoricalIdentityRequiresReview() {
        preview(row(2, "PETR4", "XP", List.of()));
        Asset current = mock(Asset.class);
        Asset historical = mock(Asset.class);
        when(current.getId()).thenReturn(45L);
        when(historical.getId()).thenReturn(46L);
        when(assets.findByNormalizedTicker("PETR4")).thenReturn(List.of(current));
        when(assets.findByConfirmedB3Ticker("PETR4")).thenReturn(List.of(historical));
        when(brokers.findAll()).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals("AMBIGUOUS", result.rows().get(0).assetStatus());
        assertNull(result.rows().get(0).assetId());
        assertEquals(1, result.reviewRows());
        verify(assets, never()).save(any());
    }

    @Test
    void unmatchedTickerRemainsUnmatchedWithoutInventingIdentity() {
        preview(row(2, "DESCONHECIDO", "XP", List.of()));
        when(assets.findByNormalizedTicker("DESCONHECIDO")).thenReturn(List.of());
        when(assets.findByConfirmedB3Ticker("DESCONHECIDO")).thenReturn(List.of());
        when(brokers.findAll()).thenReturn(List.of());

        B3MatchResponse result = service.match(file());
        assertEquals("NOT_FOUND", result.rows().get(0).assetStatus());
        assertNull(result.rows().get(0).assetId());
        verify(assets, never()).save(any());
    }

}
