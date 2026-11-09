package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.shared.integration.ExternalIntegrationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BenchmarkHistoryGapDetectorTest {
    private final BenchmarkQuoteRepository repository = mock(BenchmarkQuoteRepository.class);
    private final BenchmarkHistoryGapDetector detector = new BenchmarkHistoryGapDetector(repository);
    private final BiFunction<LocalDate, LocalDate, List<BenchmarkQuote>> noCall = (start, end) -> {
        throw new AssertionError("A complete interval must not call the provider");
    };

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void completeUnorderedHistoryIsSortedAndIdenticalDuplicatesDoNotDoubleReturns(BenchmarkType type) {
        var quotes = List.of(q(type, 3), q(type, 1), q(type, 2), q(type, 2));
        assertThat(detector.verify(type, d(1), d(3), quotes, noCall))
                .extracting(BenchmarkQuote::getDate).containsExactly(d(1), d(2), d(3));
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void weekendIsNotAMissingPublication(BenchmarkType type) {
        var quotes = List.of(q(type, 18), q(type, 21));
        assertThat(detector.verify(type, d(18), d(21), quotes, noCall)).isEqualTo(quotes);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void holidayIsConfirmedByProviderWithoutInventingZeroQuotes(BenchmarkType type) {
        var quotes = List.of(q(type, 4), q(type, 8));
        assertThat(detector.verify(type, d(4), d(8), quotes, (start, end) -> {
            assertThat(start).isEqualTo(d(4));
            assertThat(end).isEqualTo(d(8));
            return quotes;
        })).isEqualTo(quotes);
        verify(repository, never()).saveAll(any());
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void confirmsAllInternalMissingDatesInOrderWithoutRepairingThem(BenchmarkType type) {
        var stored = List.of(q(type, 1), q(type, 3), q(type, 5));
        var published = List.of(q(type, 5), q(type, 4), q(type, 2), q(type, 2), q(type, 1), q(type, 3));
        for (int attempt = 0; attempt < 2; attempt++) {
            var error = catchThrowableOfType(BenchmarkHistoryIncompleteException.class,
                    () -> detector.verify(type, d(1), d(5), stored, (start, end) -> published));
            assertThat(error.missingDates()).containsExactly(d(2), d(4));
        }
        verify(repository, never()).saveAll(any());
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void detectsGapWhenEntireRequestedIntervalIsMissing(BenchmarkType type) {
        neighbours(type, 2, 1, 3);
        var error = catchThrowableOfType(BenchmarkHistoryIncompleteException.class,
                () -> detector.verify(type, d(2), d(2), List.of(), (start, end) -> {
                    assertThat(start).isEqualTo(d(1));
                    assertThat(end).isEqualTo(d(3));
                    return List.of(q(type, 1), q(type, 2), q(type, 3));
                }));
        assertThat(error.missingDates()).containsExactly(d(2));
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void entirelyEmptyHolidayRemainsEmptyAndNeighboursStayOutsideResult(BenchmarkType type) {
        neighbours(type, 7, 4, 8);
        assertThat(detector.verify(type, d(7), d(7), List.of(),
                (start, end) -> List.of(q(type, 4), q(type, 8)))).isEmpty();
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void entirelyEmptyWeekendNeedsNoProvider(BenchmarkType type) {
        neighbours(type, 19, 18, 21);
        assertThat(detector.verify(type, d(19), d(19), List.of(), noCall)).isEmpty();
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void gapsOutsideRequestedPeriodDoNotBlockIt(BenchmarkType type) {
        assertThat(detector.verify(type, d(2), d(2), List.of(q(type, 1), q(type, 2), q(type, 4)), noCall))
                .extracting(BenchmarkQuote::getDate).containsExactly(d(2));
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void providerFailureDoesNotBecomeNoPublicationAndCanBeRetried(BenchmarkType type) {
        var stored = List.of(q(type, 1), q(type, 3));
        var failure = new ExternalIntegrationException(504, "timeout");
        assertThatThrownBy(() -> detector.verify(type, d(1), d(3), stored, (start, end) -> { throw failure; }))
                .isSameAs(failure);
        assertThatThrownBy(() -> detector.verify(type, d(1), d(3), stored,
                (start, end) -> List.of(q(type, 1), q(type, 2), q(type, 3))))
                .isInstanceOf(BenchmarkHistoryIncompleteException.class);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void emptyOrTruncatedProviderHistoryCannotProveAHoliday(BenchmarkType type) {
        for (var published : List.of(List.<BenchmarkQuote>of(), List.of(q(type, 1)), List.of(q(type, 3)))) {
            assertThatThrownBy(() -> detector.verify(type, d(1), d(3), List.of(q(type, 1), q(type, 3)),
                    (start, end) -> published)).isInstanceOf(ExternalIntegrationException.class);
        }
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void knownInteriorPublicationsMustAlsoBeCoveredBySource(BenchmarkType type) {
        var stored = List.of(q(type, 1), q(type, 3), q(type, 5));
        assertThatThrownBy(() -> detector.verify(type, d(1), d(5), stored,
                (start, end) -> List.of(q(type, 1), q(type, 5))))
                .isInstanceOf(ExternalIntegrationException.class);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void conflictingProviderDuplicatesCannotPassVerification(BenchmarkType type) {
        assertThatThrownBy(() -> detector.verify(type, d(1), d(3), List.of(q(type, 1), q(type, 3)),
                (start, end) -> List.of(q(type, 1), q(type, 2),
                        new BenchmarkQuote(type, d(2), BigDecimal.ONE), q(type, 3))))
                .isInstanceOf(ExternalIntegrationException.class);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void genuineZeroIsAStoredPublication(BenchmarkType type) {
        assertThat(detector.verify(type, d(2), d(2), List.of(q(type, 2)), noCall))
                .extracting(BenchmarkQuote::getValue).containsExactly(BigDecimal.ZERO);
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void noStoredHistoryHasNoInternalGap(BenchmarkType type) {
        assertThat(detector.verify(type, d(1), d(3), List.of(), noCall)).isEmpty();
    }

    @ParameterizedTest @EnumSource(BenchmarkType.class)
    void absenceIsNotCachedAcrossChangedProviderPublications(BenchmarkType type) {
        var stored = List.of(q(type, 4), q(type, 8));
        assertThat(detector.verify(type, d(4), d(8), stored, (start, end) -> stored)).isEqualTo(stored);
        assertThatThrownBy(() -> detector.verify(type, d(4), d(8), stored,
                (start, end) -> List.of(q(type, 4), q(type, 7), q(type, 8))))
                .isInstanceOf(BenchmarkHistoryIncompleteException.class);
    }

    private void neighbours(BenchmarkType type, int requested, int before, int after) {
        when(repository.findTopByBenchmarkAndDateLessThanEqualOrderByDateDesc(type, d(requested)))
                .thenReturn(Optional.of(q(type, before)));
        when(repository.findTopByBenchmarkAndDateGreaterThanEqualOrderByDateAsc(type, d(requested)))
                .thenReturn(Optional.of(q(type, after)));
    }

    private static LocalDate d(int day) { return LocalDate.of(2026, 9, day); }
    private static BenchmarkQuote q(BenchmarkType type, int day) {
        return new BenchmarkQuote(type, d(day), BigDecimal.ZERO);
    }
}
