package com.investmanager.api.valuation;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.auth.security.JwtService;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.benchmark.client.bcb.BcbClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.investmanager.api.valuation.ValuationTerms.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "brapi.token=test-token","jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@Testcontainers
class ValuationRestIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AssetRepository assets;
    @Autowired TransactionRepository transactions;
    @Autowired PortfolioRepository portfolios;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @MockitoBean CryptoQuoteProvider crypto;
    @MockitoBean TreasuryQuoteProvider treasury;
    @MockitoBean BrapiClient brapi;
    @MockitoBean BcbClient bcb;
    @MockitoBean StringRedisTemplate redis;
    final JsonMapper json=JsonMapper.builder().build();
    Portfolio portfolio;String token;LocalDate today;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE transactions, incomes, valuation_contracts, portfolio_asset_locks, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        User user=users.save(new User("Valuation","valuation@test.local","test"));
        portfolio=portfolios.save(new Portfolio("Test","Isolated",user));token=jwt.generateToken(user);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        today=LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }
    Asset buy(String ticker,AssetType type,String quantity,String unitPrice) {
        Asset asset=new Asset();asset.setTicker(ticker);asset.setName(ticker);asset.setType(type);asset=assets.save(asset);
        transactions.save(new Transaction(portfolio,asset,TransactionType.BUY,new BigDecimal(quantity),new BigDecimal(unitPrice),today.minusDays(365)));
        return asset;
    }
    String base(){return "/api/v1/portfolios/"+portfolio.getId();}
    JsonNode request(String method,String path,Object payload,String token,int status) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getRequiredProperty("local.server.port")+path))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json");
        builder.method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path+" -> "+response.body()).isEqualTo(status);
            return response.body().isBlank()?null:json.readTree(response.body());
        }
    }
    @Test void btcEthAndUnsupportedSolShareAdditiveContractWithoutChangingAccounting() throws Exception {
        buy("BTC",AssetType.CRYPTO,"0.00000001","300000");buy("ETH",AssetType.CRYPTO,"0.12","10000");buy("SOL",AssetType.CRYPTO,"2","100");
        when(crypto.quote(SupportedCryptocurrency.BTC)).thenReturn(new MarketQuote(new BigDecimal("350000"),"BRL",Instant.now(),"CoinGecko"));
        when(crypto.quote(SupportedCryptocurrency.ETH)).thenReturn(new MarketQuote(new BigDecimal("15000"),"BRL",Instant.now(),"CoinGecko"));
        var result=request("GET",base()+"/positions/valuations",null,token,200);
        assertThat(result.size()).isEqualTo(3);
        for(var row:result) {
            if(row.get("assetTicker").asString().equals("SOL")) {
                assertThat(row.get("currentValue").isNull()).isTrue();assertThat(row.get("totalCost").decimalValue()).isEqualByComparingTo("200");
                assertThat(row.get("valuationStatus").asString()).isEqualTo("UNAVAILABLE");
            } else assertThat(row.get("valuationStatus").asString()).isEqualTo("AVAILABLE");
        }
        assertThat(transactions.count()).isEqualTo(3);assertThat(assets.count()).isEqualTo(3);
        assertThat(request("GET",base()+"/positions",null,token,200).size()).isEqualTo(3);
        verify(crypto,times(2)).quote(any());verifyNoInteractions(brapi,bcb,treasury);
    }
    @Test void contractMigrationRoundTripAndFixedIncomeValuation() throws Exception {
        Asset asset=buy("CDB1",AssetType.FIXED_INCOME,"100","10");
        String path=base()+"/assets/"+asset.getId()+"/valuation-contract";
        assertThat(request("GET",path,null,token,200)).isNull();
        var terms=new ValuationTerms(Instrument.CDB,RateType.PRE_FIXED,BigDecimal.TEN,null,DayCount.ACT_365,today.minusDays(365),today.plusYears(1),null,null,true,true);
        assertThat(request("PUT",path,terms,token,200).get("annualRate").decimalValue()).isEqualByComparingTo("10");
        assertThat(request("GET",path,null,token,200).get("instrument").asString()).isEqualTo("CDB");
        var value=request("GET",base()+"/positions/valuations",null,token,200).get(0);
        assertThat(value.get("currentValue").decimalValue()).isEqualByComparingTo("1100");
        assertThat(value.get("totalCost").decimalValue()).isEqualByComparingTo("1000");
        assertThat(value.get("valuationStatus").asString()).isEqualTo("ESTIMATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM valuation_contracts",Integer.class)).isEqualTo(1);
        verifyNoInteractions(crypto,treasury,brapi,bcb);
    }
    @Test void unauthorizedUserCannotReadOrChangeContractsOrTriggerQuotes() throws Exception {
        Asset asset=buy("CDB",AssetType.FIXED_INCOME,"1","100");
        String other=jwt.generateToken(users.save(new User("Other","other@test.local","test")));
        String path=base()+"/assets/"+asset.getId()+"/valuation-contract";
        request("GET",base()+"/positions/valuations",null,other,404);
        request("GET",path,null,other,404);
        request("PUT",path,new ValuationTerms(Instrument.CDB,RateType.PRE_FIXED,BigDecimal.TEN,null,DayCount.ACT_365,today.minusDays(365),today.plusYears(1),null,null,true,true),other,404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM valuation_contracts",Integer.class)).isZero();
        verifyNoInteractions(crypto,treasury,brapi,bcb);
    }
    @Test void invalidContractDoesNotWriteDefaults() throws Exception {
        Asset asset=buy("LCI",AssetType.FIXED_INCOME,"1","100");
        request("PUT",base()+"/assets/"+asset.getId()+"/valuation-contract",new ValuationTerms(Instrument.LCI,null,null,null,null,null,today.plusYears(1),null,null,null,true),token,400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM valuation_contracts",Integer.class)).isZero();
        assertThat(request("GET",base()+"/positions/valuations",null,token,200).get(0).get("currentValue").isNull()).isTrue();
    }
}
