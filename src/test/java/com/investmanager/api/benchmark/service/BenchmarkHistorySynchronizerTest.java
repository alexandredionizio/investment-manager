package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.shared.integration.ExternalIntegrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.investmanager.api.benchmark.service.BenchmarkHistoryFixture.q;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BenchmarkHistorySynchronizerTest {
    private final BenchmarkHistoryFixture f = new BenchmarkHistoryFixture();
    private static LocalDate d(String text) { return LocalDate.parse(text); }
    private List<BenchmarkQuote> load(BenchmarkType type, String from, String to, List<BenchmarkQuote> data) {
        return f.synchronizer.load(type, d(from), d(to), (a,b) -> data);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void recoversMultipleInternalGapsSortsAndDeduplicatesWithoutChangingValidValues(BenchmarkType type) {
        f.put(q(type,"2026-09-01","10")); f.put(q(type,"2026-09-03","12")); f.put(q(type,"2026-09-07","14"));
        var data=List.of(q(type,"2026-09-07","999"),q(type,"2026-09-04","13"),q(type,"2026-09-02","11"),
                q(type,"2026-09-01","999"),q(type,"2026-09-03","12"),q(type,"2026-09-02","11"));
        var result=load(type,"2026-09-01","2026-09-07",data);
        assertThat(result).extracting(BenchmarkQuote::getDate).containsExactly(d("2026-09-01"),d("2026-09-02"),d("2026-09-03"),d("2026-09-04"),d("2026-09-07"));
        assertThat(result.getFirst().getValue()).isEqualByComparingTo("10");
        assertThat(result.getLast().getValue()).isEqualByComparingTo("14");
        assertThat(load(type,"2026-09-01","2026-09-07",null)).hasSize(5);
        verify(f.writer,times(1)).insertMissing(any());
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void providerConfirmsHolidaysAndNegativeCacheAvoidsRepeatedQueries(BenchmarkType type) {
        var data=List.of(q(type,"2026-09-04","10"),q(type,"2026-09-08","11"));
        data.forEach(f::put);
        AtomicInteger calls=new AtomicInteger();
        for(int i=0;i<2;i++) assertThat(f.synchronizer.load(type,d("2026-09-04"),d("2026-09-08"),(a,b)->{
            calls.incrementAndGet();return data;
        })).hasSize(2);
        assertThat(calls.get()).isEqualTo(1);
        verifyNoInteractions(f.writer);
        verify(f.cache).put(anyString(),anyString(),eq(Duration.ofHours(24)));
    }

    @Test void calendarsAreIndependent() {
        load(BenchmarkType.CDI,"2026-09-07","2026-09-07",List.of());
        var result=load(BenchmarkType.IBOV,"2026-09-07","2026-09-07",List.of(q(BenchmarkType.IBOV,"2026-09-07","100")));
        assertThat(result).hasSize(1);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void weekendOnlyDoesNotRequireProviderOrFabricateQuotes(BenchmarkType type) {
        assertThat(load(type,"2026-09-05","2026-09-06",null)).isEmpty();
        verifyNoInteractions(f.writer,f.cache);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void recoversEmptyRequestedIntervalAndBothEdges(BenchmarkType type) {
        var data=List.of(q(type,"2026-09-01","10"),q(type,"2026-09-02","11"),q(type,"2026-09-03","12"));
        f.put(data.get(1));
        assertThat(load(type,"2026-09-01","2026-09-03",data)).hasSize(3);
        assertThat(load(type,"2026-09-02","2026-09-02",null)).hasSize(1);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void rejectsTruncatedProviderHistoryAndPreservesDatabase(BenchmarkType type) {
        f.put(q(type,"2026-09-01","10")); f.put(q(type,"2026-09-03","12"));
        assertThatThrownBy(()->load(type,"2026-09-01","2026-09-03",List.of(q(type,"2026-09-02","11"))))
                .isInstanceOf(ExternalIntegrationException.class);
        assertThat(f.rows).hasSize(2); verifyNoInteractions(f.writer); assertThat(f.cached).isEmpty();
    }

    @Test void invalidConflictingOrOutOfRangePayloadNeverPartiallyPersists() {
        for(var data:List.of(
                List.of(q(BenchmarkType.IBOV,"2026-09-01","100"),q(BenchmarkType.IBOV,"2026-09-02","0")),
                List.of(q(BenchmarkType.IBOV,"2026-09-01","100"),q(BenchmarkType.IBOV,"2026-09-01","101")),
                List.of(q(BenchmarkType.IBOV,"2027-01-01","100")),
                List.of(q(BenchmarkType.IBOV,"2026-09-01","0.123456789")))) {
            assertThatThrownBy(()->load(BenchmarkType.IBOV,"2026-09-01","2026-09-03",data))
                    .isInstanceOf(ExternalIntegrationException.class);
        }
        verifyNoInteractions(f.writer); assertThat(f.rows).isEmpty(); assertThat(f.cached).isEmpty();
    }

    @Test void cdiZeroIsValidButImpossibleRateIsRejected() {
        assertThat(load(BenchmarkType.CDI,"2026-09-01","2026-09-01",List.of(q(BenchmarkType.CDI,"2026-09-01","0")))).hasSize(1);
        f.cached.clear();
        assertThatThrownBy(()->load(BenchmarkType.CDI,"2026-09-02","2026-09-02",List.of(q(BenchmarkType.CDI,"2026-09-02","-100"))))
                .isInstanceOf(ExternalIntegrationException.class);
    }

    @Test void externalFailureDoesNotAlterStoredValuesOrCacheAbsence() {
        f.put(q(BenchmarkType.CDI,"2026-09-01","0.05"));
        assertThatThrownBy(()->f.synchronizer.load(BenchmarkType.CDI,d("2026-09-01"),d("2026-09-03"),(a,b)->{
            throw new ExternalIntegrationException(504,"timeout");
        })).isInstanceOf(ExternalIntegrationException.class);
        assertThat(f.rows).hasSize(1); assertThat(f.cached).isEmpty(); verifyNoInteractions(f.writer);
    }

    @Test void failedPersistenceIsNotCachedAndMissingRowsNeverBecomeReturns() {
        doNothing().when(f.writer).insertMissing(any());
        assertThatThrownBy(()->load(BenchmarkType.CDI,"2026-09-01","2026-09-01",List.of(q(BenchmarkType.CDI,"2026-09-01","0.05"))))
                .isInstanceOf(BenchmarkHistoryIncompleteException.class);
        assertThat(f.cached).isEmpty();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable")).when(f.writer).insertMissing(any());
        assertThatThrownBy(()->load(BenchmarkType.CDI,"2026-09-01","2026-09-01",List.of(q(BenchmarkType.CDI,"2026-09-01","0.05"))))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(f.cached).isEmpty();
    }

    @Test void futureDatesAreNotQueriedAndRecentAbsenceExpiresQuickly() {
        assertThat(load(BenchmarkType.CDI,"2026-11-07","2026-12-01",null)).isEmpty();
        assertThat(f.synchronizer.load(BenchmarkType.CDI,d("2026-11-06"),d("2026-12-01"),(a,b)->{
            assertThat(b).isEqualTo(d("2026-11-06"));return List.of();
        })).isEmpty();
        verify(f.cache).put(anyString(),anyString(),eq(Duration.ofMinutes(5)));
    }

    @Test void cacheExpiryAllowsLatePublicationAndCorruptionRefetches() {
        assertThat(load(BenchmarkType.CDI,"2026-09-01","2026-09-01",List.of())).isEmpty();
        f.cached.replaceAll((k,v)->"broken");
        assertThat(load(BenchmarkType.CDI,"2026-09-01","2026-09-01",List.of(q(BenchmarkType.CDI,"2026-09-01","0.05")))).hasSize(1);
        verify(f.cache).invalidEntry();
    }

    @Test void cachedAbsenceCannotHideNewDatabasePublications() {
        load(BenchmarkType.CDI,"2026-09-01","2026-09-02",List.of());
        f.put(q(BenchmarkType.CDI,"2026-09-01","0.05"));
        assertThat(load(BenchmarkType.CDI,"2026-09-01","2026-09-02",List.of(q(BenchmarkType.CDI,"2026-09-01","0.05"),q(BenchmarkType.CDI,"2026-09-02","0.06")))).hasSize(2);
    }

    @Test void largeRecoveryIsBoundedAndResumesUsingSuccessfulWindows() {
        AtomicInteger calls=new AtomicInteger();
        java.util.function.BiFunction<LocalDate,LocalDate,List<BenchmarkQuote>> source=(a,b)->{calls.incrementAndGet();return List.of();};
        assertThatThrownBy(()->f.synchronizer.load(BenchmarkType.CDI,d("2025-01-01"),d("2026-09-30"),source))
                .isInstanceOf(BenchmarkHistoryIncompleteException.class);
        assertThat(calls.get()).isEqualTo(4);
        assertThat(f.synchronizer.load(BenchmarkType.CDI,d("2025-01-01"),d("2026-09-30"),source)).isEmpty();
        assertThat(calls.get()).isEqualTo(7);
    }

    @Test void redisConnectivityBypassStillRecoversFromProvider() {
        when(f.cache.get(anyString())).thenReturn(null);
        doNothing().when(f.cache).put(anyString(),anyString(),any());
        assertThat(load(BenchmarkType.CDI,"2026-09-01","2026-09-01",List.of(q(BenchmarkType.CDI,"2026-09-01","0.05")))).hasSize(1);
    }
}
