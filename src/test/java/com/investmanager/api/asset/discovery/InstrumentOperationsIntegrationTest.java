package com.investmanager.api.asset.discovery;
import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.auth.security.JwtService;
import com.investmanager.api.broker.*;
import com.investmanager.api.broker.repository.BrokerRepository;
import com.investmanager.api.portfolio.*;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.dto.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.transaction.service.*;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "brapi.token=test-token","jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@Testcontainers
class InstrumentOperationsIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AssetRepository assets;
    @Autowired TransactionRepository transactions;
    @Autowired PortfolioRepository portfolios;
    @Autowired UserRepository users;
    @Autowired BrokerRepository brokers;
    @Autowired TransactionOperationsService operations;
    @Autowired TransactionService accounting;
    @Autowired SaleOptionsService saleOptions;
    @Autowired InstrumentIdentityService identities;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired JwtService jwt;
    @Autowired Environment environment;
    @MockitoBean BrapiCatalogClient catalog;
    User user, other;
    Portfolio portfolio;
    Broker broker;
    String token;
    final LocalDate date=LocalDate.of(2026,9,1);
    @BeforeEach void setup() {
        // Exclusively the synthetic @ServiceConnection database.
        jdbc.execute("TRUNCATE transactions, incomes, portfolio_asset_locks, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        user=users.save(new User("Discovery", "discovery@test.local", "test"));
        other=users.save(new User("Other", "other@test.local", "test"));
        portfolio=portfolios.save(new Portfolio("Discovery", "Test", user));
        broker=new Broker(); broker.setName("Test"); broker=brokers.save(broker); token=jwt.generateToken(user);
        when(catalog.confirm(anyString())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return evidence(inv.getArgument(0),null,List.of());
        });
    }
    InstrumentEvidence evidence(String ticker,String isin,List<InstrumentEvidence.Alias> aliases) {
        return new InstrumentEvidence(ticker,"Test "+ticker,AssetType.STOCK,"B3",isin,aliases,"BRAPI",true);
    }
    PurchaseRequest request(String ticker) { return new PurchaseRequest(portfolio.getId(),ticker,broker.getId(),BigDecimal.TEN,BigDecimal.TEN,date); }
    Asset register(InstrumentEvidence evidence) { return new TransactionTemplate(manager).execute(s -> identities.registerConfirmed(evidence)); }
    Asset legacy(String ticker,AssetType type,String exchange) {
        Asset a=new Asset(); a.setTicker(ticker);a.setName(ticker);a.setType(type);a.setExchange(exchange);return assets.save(a);
    }
    @Test void searchDoesNotWriteAndPurchaseCreatesAtomically() throws Exception {
        when(catalog.search("PETR",1,10)).thenReturn(new BrapiCatalogClient.Page(List.of(evidence("PETR4",null,List.of())),1,false));
        var search=http("GET","/api/v1/assets/search?query=PETR",null,token);
        assertThat(search.statusCode()).isEqualTo(200); assertThat(search.body()).contains("PETR4","assetId");
        assertThat(assets.count()).isZero(); assertThat(transactions.count()).isZero();
        var buy=http("POST","/api/v1/transactions/purchases",purchaseJson("PETR4"),token);
        assertThat(buy.statusCode()).as(buy.body()).isEqualTo(201);
        assertThat(assets.count()).isEqualTo(1); assertThat(transactions.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from asset_identifiers",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select recorded_ticker from transactions",String.class)).isEqualTo("PETR4");
    }
    @Test void failedPurchaseRollsBackAssetAndIdentifiers() {
        var bad=new PurchaseRequest(portfolio.getId(),"PETR4",9999L,BigDecimal.TEN,BigDecimal.TEN,date);
        assertThatThrownBy(() -> operations.purchase(bad,user.getId())).isInstanceOf(com.investmanager.api.broker.exception.BrokerNotFoundException.class);
        assertThat(assets.count()).isZero();assertThat(transactions.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from asset_identifiers",Long.class)).isZero();
    }
    @Test void providerOutageOrInactiveInstrumentCannotRegisterAnything() {
        when(catalog.confirm("PETR4")).thenThrow(new InstrumentException(503,"INSTRUMENT_CATALOG_UNAVAILABLE","Catálogo indisponível"));
        assertThatThrownBy(() -> operations.purchase(request("PETR4"),user.getId())).isInstanceOf(InstrumentException.class);
        doThrow(InstrumentException.ineligible()).when(catalog).confirm("PETR4");
        assertThatThrownBy(() -> operations.purchase(request("PETR4"),user.getId())).isInstanceOf(InstrumentException.class);
        assertThat(assets.count()).isZero(); assertThat(transactions.count()).isZero();
    }
    @Test void simultaneousPurchasesReuseOneAssetAcrossPortfolios() throws Exception {
        Portfolio second=portfolios.save(new Portfolio("Second","Test",user));
        CyclicBarrier ready=new CyclicBarrier(4);
        when(catalog.confirm("PETR4")).thenAnswer(inv -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); ready.await(10,TimeUnit.SECONDS); return evidence("PETR4",null,List.of()); });
        try (var pool=Executors.newFixedThreadPool(4)) {
            List<Future<TransactionResponse>> futures=new ArrayList<>();
            for(int i=0;i<4;i++) { long portfolioId=i%2==0?portfolio.getId():second.getId();
                futures.add(pool.submit(() -> operations.purchase(new PurchaseRequest(portfolioId,"PETR4",broker.getId(),BigDecimal.TEN,BigDecimal.TEN,date),user.getId()))); }
            Set<Long> ids=new HashSet<>();for(var f:futures)ids.add(f.get(20,TimeUnit.SECONDS).assetId());
            assertThat(ids).hasSize(1);
        }
        assertThat(assets.count()).isEqualTo(1);assertThat(transactions.count()).isEqualTo(4);
        assertThat(saleOptions.find(portfolio.getId(),user.getId(),date).getFirst().quantityAtDate()).isEqualByComparingTo("20");
    }
    @Test void exactLegacyIdentityIsReusedButAmbiguousLegacyRowsRequireReview() {
        Asset original=legacy(" petr4 ",AssetType.STOCK,null);
        assertThat(operations.purchase(request("PETR4"),user.getId()).assetId()).isEqualTo(original.getId());
        legacy("PETR4",AssetType.STOCK,"B3");
        assertThatThrownBy(() -> operations.purchase(request("PETR4"),user.getId())).isInstanceOf(InstrumentException.class)
                .extracting(e -> ((InstrumentException)e).status()).isEqualTo(409);
        assertThat(transactions.count()).isEqualTo(1);
    }
    @Test void verifiedRenameReusesIdentityAndKeepsHistoricalTickerAndOrigin() {
        var first=operations.purchase(request("VIIA3"),user.getId());
        when(catalog.confirm("BHIA3")).thenReturn(evidence("BHIA3",null,List.of(new InstrumentEvidence.Alias("VIIA3",date.plusDays(1)))));
        var renamed=operations.purchase(request("BHIA3"),user.getId());
        assertThat(renamed.assetId()).isEqualTo(first.assetId());assertThat(renamed.assetTicker()).isEqualTo("BHIA3");
        assertThat(accounting.findById(first.id(),user.getId()).assetTicker()).isEqualTo("VIIA3");
        assertThat(identities.identifiers(first.assetId())).extracting(InstrumentIdentityService.Identifier::value).containsExactly("BHIA3","VIIA3");
        assertThat(assets.count()).isEqualTo(1);
        assertThat(saleOptions.find(portfolio.getId(),user.getId(),date).getFirst().quantityAtDate()).isEqualByComparingTo("20");
    }
    @Test void isinReconcilesVerifiedRenameAndConflictingIsinDoesNotMerge() {
        Asset first=register(evidence("AAAA3","BRAAAAACNOR0",List.of()));
        assertThat(register(evidence("BBBB3","BRAAAAACNOR0",List.of())).getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> register(evidence("BBBB3","BRBBBBACNOR0",List.of()))).isInstanceOf(InstrumentException.class);
        assertThat(assets.count()).isEqualTo(1);
        assertThat(identities.identifiers(first.getId())).hasSize(3);
    }
    @Test void differentTickersWithoutIsinOrConfirmedAliasesRemainDifferent() {
        Asset a=register(evidence("AAAA3",null,List.of())); Asset b=register(evidence("BBBB3",null,List.of()));
        assertThat(a.getId()).isNotEqualTo(b.getId());assertThat(assets.count()).isEqualTo(2);
    }
    @Test void knownSeparateAssetsAreNeverMergedByRenameOrClassConflict() {
        register(evidence("AAAA3",null,List.of()));register(evidence("BBBB3",null,List.of()));
        assertThatThrownBy(() -> register(evidence("BBBB3",null,List.of(new InstrumentEvidence.Alias("AAAA3",date)))))
                .isInstanceOf(InstrumentException.class);
        assertThatThrownBy(() -> register(new InstrumentEvidence("AAAA3","Test",AssetType.FII,"B3",null,List.of(),"OTHER_SOURCE",true)))
                .isInstanceOf(InstrumentException.class);
        assertThat(assets.count()).isEqualTo(2);
    }
    @Test void historicalPositionIncludesAssetSoldTodayButChronologyStillRejectsExcess() {
        var buy=operations.purchase(request("PETR4"),user.getId());
        accounting.create(new TransactionRequest(portfolio.getId(),buy.assetId(),broker.getId(),TransactionType.SELL,BigDecimal.TEN,BigDecimal.TEN,date.plusDays(10)),user.getId());
        assertThat(saleOptions.find(portfolio.getId(),user.getId(),date.plusDays(10))).isEmpty();
        var historical=saleOptions.find(portfolio.getId(),user.getId(),date).getFirst();
        assertThat(historical.quantityAtDate()).isEqualByComparingTo("10");assertThat(historical.chronologicalLimit()).isZero();
        assertThatThrownBy(() -> operations.create(new TransactionRequest(portfolio.getId(),buy.assetId(),broker.getId(),TransactionType.SELL,BigDecimal.ONE,BigDecimal.TEN,date),user.getId()))
                .isInstanceOf(InsufficientPositionException.class);
        assertThat(transactions.count()).isEqualTo(2);
    }
    @Test void sameDayIncludesAllCommittedOperationsAndSellNeedsNoCatalog() {
        var buy=operations.purchase(request("PETR4"),user.getId());
        clearInvocations(catalog);
        doThrow(InstrumentException.ineligible()).when(catalog).confirm(anyString());
        operations.create(new TransactionRequest(portfolio.getId(),buy.assetId(),broker.getId(),TransactionType.SELL,BigDecimal.ONE,BigDecimal.TEN,date),user.getId());
        assertThat(saleOptions.find(portfolio.getId(),user.getId(),date).getFirst().quantityAtDate()).isEqualByComparingTo("9");
        assertThat(saleOptions.find(portfolio.getId(),user.getId(),date.minusDays(1))).isEmpty();
        verifyNoInteractions(catalog);
    }
    @Test void legacyBuyEndpointCannotBypassEligibilityAndNonB3IsSeparate() {
        Asset stock=legacy("PETR4",AssetType.STOCK,"B3"); when(catalog.confirm("PETR4")).thenThrow(InstrumentException.ineligible());
        assertThatThrownBy(() -> operations.create(new TransactionRequest(portfolio.getId(),stock.getId(),broker.getId(),TransactionType.BUY,BigDecimal.ONE,BigDecimal.ONE,date),user.getId()))
                .isInstanceOf(InstrumentException.class);
        clearInvocations(catalog);
        for(AssetType type:List.of(AssetType.CRYPTO,AssetType.FIXED_INCOME,AssetType.TREASURE)) {
            Asset asset=legacy(type==AssetType.CRYPTO?"BTC":type.name(),type,"OTHER");
            operations.create(new TransactionRequest(portfolio.getId(),asset.getId(),broker.getId(),TransactionType.BUY,BigDecimal.ONE,BigDecimal.ONE,date),user.getId());
        }
        verifyNoInteractions(catalog);assertThat(transactions.count()).isEqualTo(3);
    }
    @Test void unauthorizedPortfolioCannotQueryOrRegisterAndEndpointsRequireAuthentication() throws Exception {
        assertThatThrownBy(() -> operations.purchase(request("PETR4"),other.getId())).isInstanceOf(com.investmanager.api.portfolio.exception.PortfolioNotFoundException.class);
        assertThatThrownBy(() -> saleOptions.find(portfolio.getId(),other.getId(),date)).isInstanceOf(com.investmanager.api.portfolio.exception.PortfolioNotFoundException.class);
        verifyNoInteractions(catalog);assertThat(assets.count()).isZero();
        assertThat(http("GET","/api/v1/assets/search?query=PETR",null,null).statusCode()).isIn(401,403);
        assertThat(http("POST","/api/v1/transactions/purchases",purchaseJson("PETR4"),null).statusCode()).isIn(401,403);
        assertThat(http("GET","/api/v1/transactions/portfolio/"+portfolio.getId()+"/sale-options?date=2026-09-01",null,jwt.generateToken(other)).statusCode()).isEqualTo(404);
    }
    @Test void restValidationRejectsMalformedRequestsWithoutWrites() throws Exception {
        assertThat(http("POST","/api/v1/transactions/purchases",purchaseJson("BTC"),token).statusCode()).isEqualTo(400);
        assertThat(http("POST","/api/v1/transactions/purchases",purchaseJson("PETR4").replace("\"quantity\":10","\"quantity\":0"),token).statusCode()).isEqualTo(400);
        assertThat(http("GET","/api/v1/transactions/portfolio/"+portfolio.getId()+"/sale-options?date=invalid",null,token).statusCode()).isEqualTo(400);
        verifyNoInteractions(catalog);assertThat(transactions.count()).isZero();
    }
    String purchaseJson(String ticker) { return """
        {"portfolioId":%d,"ticker":"%s","brokerId":%d,"quantity":10,"unitPrice":10,"transactionDate":"2026-09-01"}
        """.formatted(portfolio.getId(),ticker,broker.getId()); }
    HttpResponse<String> http(String method,String path,String body,String auth) throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var req=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getRequiredProperty("local.server.port")+path))
                    .header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            if(auth!=null)req.header("Authorization","Bearer "+auth);
            return client.send(req.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
