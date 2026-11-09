package com.investmanager.api.portfoliohistory;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.benchmark.controller.BenchmarkComparisonController;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.benchmark.calculator.CdiReturnCalculator;
import com.investmanager.api.benchmark.calculator.IbovReturnCalculator;
import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.service.*;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.returnrate.*;
import com.investmanager.api.portfoliohistory.service.*;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.quote.service.HistoricalQuoteService;
import com.investmanager.api.shared.exception.GlobalExceptionHandler;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardDataAvailabilityTest {
    private final LocalDate first = LocalDate.of(2026, 9, 20);
    private final LocalDate end = first.plusDays(1);
    private PortfolioRepository portfolios;
    private IncomeRepository incomeRepository;
    private TransactionRepository transactions;
    private HistoricalQuoteService quotes;
    private CdiBenchmarkService cdi;
    private IbovBenchmarkService ibov;
    private PortfolioReturnService returns;
    private PortfolioHistoryService history;
    private BenchmarkComparisonService comparison;
    private Asset asset;
    private Portfolio portfolio;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        portfolios = mock(PortfolioRepository.class);
        incomeRepository = mock(IncomeRepository.class);
        transactions = mock(TransactionRepository.class);
        quotes = mock(HistoricalQuoteService.class);
        cdi = mock(CdiBenchmarkService.class);
        ibov = mock(IbovBenchmarkService.class);
        portfolio = new Portfolio();
        asset = new Asset();
        asset.setTicker("ITUB4");
        when(portfolios.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(portfolio));
        var positions = new HistoricalPositionCalculator();
        var resolver = new HistoricalQuoteResolver();
        returns = new PortfolioReturnService(portfolios, transactions, incomeRepository, quotes,
                new PortfolioHistoryDateGenerator(), new PortfolioHistoryDateRangeResolver(), positions, resolver,
                new PortfolioHistoricalValueCalculator(positions, resolver), new com.investmanager.api.income.service.IncomePaymentPolicy(java.time.Clock.fixed(java.time.Instant.parse("2026-10-31T15:00:00Z"), java.time.ZoneId.of("America/Sao_Paulo"))));
        history = new PortfolioHistoryService(portfolios, transactions, quotes,
                new PortfolioHistoryGranularityResolver(), new PortfolioHistoryDateRangeResolver(),
                new PortfolioHistoryDateGenerator(), positions, resolver);
        comparison = new BenchmarkComparisonService(returns, cdi, ibov, new CdiReturnCalculator(), new IbovReturnCalculator());
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioReturnController(returns))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @EnumSource(PortfolioHistoryPeriod.class)
    void emptyPortfolioHasNoCalculatedReturnsOrHistoryForEveryPeriod(PortfolioHistoryPeriod period) {
        var result = returns.getReturns(1L, 10L, period, first, end, end);
        assertThat(result.totalReturn()).isNull();
        assertThat(result.points()).isEmpty();
        assertThat(result.startDate()).isBeforeOrEqualTo(result.endDate());
        assertThat(history.getHistory(1L, 10L, period, first, end, end).points()).isEmpty();
        assertThat(comparison.getComparison(1L, 10L, period, first, end, end)).isEmpty();
        verifyNoInteractions(quotes, cdi, ibov);
    }

    @Test
    void firstDayHasValuationButNotYetMeasuredReturn() {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN));
        var result = returns.getReturns(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, first);
        assertThat(result.totalReturn()).isNull();
        assertThat(result.points()).isEmpty();
        assertThat(history.getHistory(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, first).points())
                .singleElement().satisfies(point -> assertThat(point.value()).isEqualByComparingTo("1000"));
    }

    @Test
    void singleOlderPurchaseIsEnoughToMeasureRealZeroWithoutSnapshots() {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN), new HistoricalQuote(end, BigDecimal.TEN));
        var result = returns.getReturns(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end);
        assertThat(result.totalReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().getFirst().dailyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.points().getLast().dailyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void oneDayCustomPeriodCanHaveMeasuredReturnWhenThereIsPreviousCapital() {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN), new HistoricalQuote(end, new BigDecimal("11")));
        assertThat(returns.getReturns(1L, 10L, PortfolioHistoryPeriod.CUSTOM, end, end, null).totalReturn())
                .isEqualByComparingTo("0.1");
    }

    @Test
    void periodBeforeFirstTransactionAndFutureOnlyPortfolioAreEmpty() {
        activity(buy(end.plusDays(1)));
        for (var period : PortfolioHistoryPeriod.values()) {
            assertThat(returns.getReturns(1L, 10L, period, first, end, end).totalReturn()).isNull();
            assertThat(history.getHistory(1L, 10L, period, first, end, end).points()).isEmpty();
        }
        verifyNoInteractions(quotes);
    }

    @Test
    void zeroValuationAfterClosingPositionDoesNotInventReturnWithoutCapital() {
        activity(buy(first.minusDays(3)), new Transaction(portfolio, asset, TransactionType.SELL,
                new BigDecimal("100"), BigDecimal.TEN, first.minusDays(2)));
        var result = returns.getReturns(1L, 10L, PortfolioHistoryPeriod.CUSTOM, first, end, null);
        assertThat(result.totalReturn()).isNull();
        assertThat(result.points()).isEmpty();
        assertThat(history.getHistory(1L, 10L, PortfolioHistoryPeriod.CUSTOM, first, end, null).points())
                .allSatisfy(point -> assertThat(point.value()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void missingRequiredQuoteDoesNotBecomeZeroOrPartialValuation() {
        activity(buy(first));
        prices(new HistoricalQuote(end, BigDecimal.TEN));
        assertThatThrownBy(() -> returns.getReturns(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end))
                .isInstanceOf(HistoricalQuoteUnavailableException.class);
        assertThatThrownBy(() -> history.getHistory(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end))
                .isInstanceOf(HistoricalQuoteUnavailableException.class);
    }

    @Test
    void missingBenchmarkDoesNotHidePortfolioOrOtherBenchmarkOrInventZero() {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN));
        when(ibov.load(any(), any())).thenReturn(List.of(
                new BenchmarkQuote(BenchmarkType.IBOV, first, new BigDecimal("100")),
                new BenchmarkQuote(BenchmarkType.IBOV, end, new BigDecimal("110"))));
        var points = comparison.getComparison(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end);
        assertThat(points).hasSize(2).allSatisfy(point -> {
            assertThat(point.cdiReturn()).isNull();
            assertThat(point.portfolioReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        assertThat(points.getLast().ibovReturn()).isEqualByComparingTo("0.1");
    }

    @Test
    void benchmarkStartingLateIsNotComparedAsThoughItHadInitialBaseline() {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN));
        when(ibov.load(any(), any())).thenReturn(List.of(new BenchmarkQuote(BenchmarkType.IBOV, end, BigDecimal.TEN)));
        when(cdi.load(any(), any())).thenReturn(List.of(new BenchmarkQuote(BenchmarkType.CDI, end, BigDecimal.ZERO)));
        assertThat(comparison.getComparison(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end))
                .allSatisfy(point -> { assertThat(point.ibovReturn()).isNull(); assertThat(point.cdiReturn()).isNull(); });
    }

    @Test
    void providerTimeoutIsStillAnError() {
        activity(buy(first));
        var failure = new ResourceAccessException("timeout");
        when(quotes.getHistoricalQuotes(anyString(), any(LocalDate.class), any(LocalDate.class), any())).thenThrow(failure);
        assertThatThrownBy(() -> returns.getReturns(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end)).isSameAs(failure);
        assertThatThrownBy(() -> history.getHistory(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end)).isSameAs(failure);
    }

    @Test
    void benchmarkTechnicalFailureIsNotAnEmptyComparison() {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN));
        var failure = new IllegalStateException("provider failure");
        when(cdi.load(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> comparison.getComparison(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end)).isSameAs(failure);
    }

    @Test
    void databaseFailureIsNotAnEmptyPortfolio() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(1L, 10L)).thenThrow(failure);
        assertThatThrownBy(() -> returns.getReturns(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end)).isSameAs(failure);
    }

    @Test
    void ownershipAndPeriodValidationStillApplyToEmptyPortfolio() {
        assertThatThrownBy(() -> returns.getReturns(1L, 10L, PortfolioHistoryPeriod.CUSTOM, end, first, null))
                .isInstanceOf(IllegalArgumentException.class);
        when(portfolios.findByIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> returns.getReturns(1L, 10L, PortfolioHistoryPeriod.ALL, null, null, end))
                .isInstanceOf(PortfolioNotFoundException.class);
    }

    @Test
    void emptyAllHttpResponseKeepsStructureAndSerializesExplicitNull() throws Exception {
        mvc.perform(get("/api/v1/portfolios/1/returns").param("period", "ALL")
                .principal(new UsernamePasswordAuthenticationToken("10", null, List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.portfolioId").value(1))
                .andExpect(jsonPath("$.startDate").isString()).andExpect(jsonPath("$.endDate").isString())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"totalReturn\":null")))
                .andExpect(jsonPath("$.points").isEmpty());
    }

    @Test
    void quoteUnavailableHasExplicitHttpErrorWithoutInventingSuccess() throws Exception {
        activity(buy(first));
        mvc.perform(get("/api/v1/portfolios/1/returns").param("period", "CUSTOM")
                .param("startDate", first.toString()).param("endDate", end.toString())
                .principal(new UsernamePasswordAuthenticationToken("10", null, List.of())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Cotação indisponível"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,404", "false,404", "true,200", "false,200"})
    void sgsNoValuesDistinguishesEmptyHistoryFromContradictoryStoredCoverage(boolean cachedCdi, int providerStatus) throws Exception {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN));
        when(ibov.load(any(), any())).thenReturn(List.of(
                new BenchmarkQuote(BenchmarkType.IBOV, first, new BigDecimal("100")),
                new BenchmarkQuote(BenchmarkType.IBOV, end, new BigDecimal("110"))));
        var storedQuote = new BenchmarkQuote(BenchmarkType.CDI, first, new BigDecimal("0.05"));
        var fixture = new com.investmanager.api.benchmark.service.BenchmarkHistoryFixture();
        var benchmarkRepository = fixture.repository;
        if (cachedCdi) fixture.put(storedQuote);
        var builder = org.springframework.web.client.RestClient.builder();
        var bcbServer = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        bcbServer.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.valueOf(providerStatus))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("""
                                {"erro":{"statusCode":404,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}
                                """));
        var realCdi = new CdiBenchmarkService(new BcbClient(builder), fixture.synchronizer);
        var realComparison = new BenchmarkComparisonService(returns, realCdi, ibov,
                new CdiReturnCalculator(), new IbovReturnCalculator());
        var comparisonMvc = MockMvcBuilders.standaloneSetup(new BenchmarkComparisonController(realComparison))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        if (cachedCdi) {
            comparisonMvc.perform(get("/api/v1/portfolios/1/benchmark-comparison")
                    .param("period", "CUSTOM").param("startDate", first.toString()).param("endDate", end.toString())
                    .principal(new UsernamePasswordAuthenticationToken("10", null, List.of())))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("Falha na integração externa"));
            assertThat(fixture.rows).hasSize(1);
            verifyNoInteractions(fixture.writer);
            bcbServer.verify();
            return;
        }
        var response = comparisonMvc.perform(get("/api/v1/portfolios/1/benchmark-comparison")
                        .param("period", "CUSTOM").param("startDate", first.toString()).param("endDate", end.toString())
                        .principal(new UsernamePasswordAuthenticationToken("10", null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].portfolioReturn").value(0))
                .andExpect(jsonPath("$[1].ibovReturn").value(0.1));
        if (cachedCdi) {
            response.andExpect(jsonPath("$[1].cdiReturn").value(0));
        } else {
            response.andExpect(content().string(org.hamcrest.Matchers.containsString("\"cdiReturn\":null")));
            response.andExpect(jsonPath("$[*].cdiReturn", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
        }
        verify(benchmarkRepository, never()).save(any());
        bcbServer.verify();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2026-10-30,0.05", "2026-10-31,0.05", "2026-11-01,0"})
    void performanceIncludesOnlyPaidIncomeEvenWhenCustomRangeExtendsIntoFuture(String payment, String expectedReturn) {
        activity(buy(first));
        prices(new HistoricalQuote(first, BigDecimal.TEN));
        LocalDate paymentDate = LocalDate.parse(payment);
        var income = new com.investmanager.api.income.Income(portfolio, asset,
                com.investmanager.api.income.IncomeType.DIVIDEND, new BigDecimal("0.50"),
                new BigDecimal("100"), first, paymentDate);
        when(incomeRepository.findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(1L, 10L))
                .thenReturn(List.of(income));
        var result = returns.getReturns(1L, 10L, PortfolioHistoryPeriod.CUSTOM, paymentDate, paymentDate, null);
        assertThat(result.totalReturn()).isEqualByComparingTo(expectedReturn);
        assertThat(result.points()).hasSize(1);
        assertThat(income.getQuantity()).isEqualByComparingTo("100");
        verify(incomeRepository, never()).save(any());
    }

    private Transaction buy(LocalDate date) {
        return new Transaction(portfolio, asset, TransactionType.BUY, new BigDecimal("100"), BigDecimal.TEN, date);
    }

    private void activity(Transaction... values) {
        when(transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(1L, 10L)).thenReturn(List.of(values));
        when(transactions.findByPortfolioIdOrderByTransactionDateAscIdAsc(1L)).thenReturn(List.of(values));
    }

    private void prices(HistoricalQuote... values) {
        when(quotes.getHistoricalQuotes(anyString(), any(LocalDate.class), any(LocalDate.class), any())).thenReturn(List.of(values));
    }
}
