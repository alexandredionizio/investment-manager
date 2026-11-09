package com.investmanager.api.b3import;

import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.discovery.BrapiCatalogClient;
import com.investmanager.api.asset.discovery.InstrumentEvidence;
import com.investmanager.api.asset.discovery.InstrumentException;
import com.investmanager.api.asset.discovery.InstrumentIdentityService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class B3AssetRegistrationServiceTest {
    private final BrapiCatalogClient catalog = mock(BrapiCatalogClient.class);
    private final InstrumentIdentityService identities = mock(InstrumentIdentityService.class);
    private final B3AssetRegistrationWriter writer = mock(B3AssetRegistrationWriter.class);
    private final B3AssetRegistrationService service = new B3AssetRegistrationService(catalog, identities, writer);
    private InstrumentEvidence evidence(String ticker, AssetType type) {
        return new InstrumentEvidence(ticker, "Nome de " + ticker, type, "B3", null, List.of(), "BRAPI", true);
    }

    @Test void rejectsUnverifiedTickerWithoutWriting() {
        when(catalog.confirm("ITSA4")).thenThrow(InstrumentException.ineligible());
        assertThrows(InstrumentException.class, () -> service.register("itsa4"));
        verifyNoInteractions(writer);
    }
    @Test void lookupNeverRegisters() {
        var e = evidence("BBAS3", AssetType.STOCK);
        when(catalog.confirm("BBAS3")).thenReturn(e);
        when(identities.resolve(e)).thenReturn(Optional.empty());
        var result = service.lookup("bbas3");
        assertEquals("BBAS3", result.ticker());
        assertEquals(AssetType.STOCK, result.type());
        verifyNoInteractions(writer);
    }
    @Test void registerDelegatesExactTrustedEvidenceWithIsin() {
        var e = new InstrumentEvidence("KNRI11", "Kinea Renda", AssetType.FII, "B3", "BRKNRICTF000", List.of(), "BRAPI", true);
        when(catalog.confirm("KNRI11")).thenReturn(e);
        when(writer.register(e)).thenReturn(new B3AssetRegistrationService.RegistrationResponse("KNRI11", 42L, true));
        var result = service.register("knri11");
        assertEquals(42L, result.assetId());
        verify(writer).register(e);
    }
    @Test void refusesUnexpectedTickerFromProvider() {
        when(catalog.confirm("WRLD11")).thenReturn(evidence("OTHER11", AssetType.ETF));
        assertThrows(InstrumentException.class, () -> service.lookup("WRLD11"));
        verifyNoInteractions(writer);
    }
    @Test void refusesUnsupportedClass() {
        when(catalog.confirm("BTC1")).thenReturn(evidence("BTC1", AssetType.CRYPTO));
        assertThrows(InstrumentException.class, () -> service.lookup("BTC1"));
        verifyNoInteractions(writer);
    }
}
