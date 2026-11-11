package com.investmanager.api.operational;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.auth.security.JwtService;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.quote.service.QuoteService;
import com.investmanager.api.shared.integration.*;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import com.sun.net.httpserver.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** Explicit local measurement, excluded by default Surefire name patterns. No real providers. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "brapi.token=synthetic-token", "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.session.metrics=OFF",
        "logging.level.com.investmanager.api.shared.integration.ExternalCalls=WARN"})
@Testcontainers
class OperationalPerformanceProbe {
    @Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Container static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static final AtomicInteger httpCalls = new AtomicInteger(), active = new AtomicInteger(), peak = new AtomicInteger();
    static final Set<String> connections = ConcurrentHashMap.newKeySet();
    static final Set<String> requests = ConcurrentHashMap.newKeySet();
    static final Map<String,AtomicInteger> callsByPath = new ConcurrentHashMap<>();
    static volatile long latencyMs = 20;
    static final HttpServer upstream = startServer();
    static final ZoneId zone = ZoneId.of("America/Sao_Paulo");
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("brapi.base-url", () -> "http://127.0.0.1:"+upstream.getAddress().getPort());
        r.add("bcb.base-url", () -> "http://127.0.0.1:"+upstream.getAddress().getPort());
        r.add("spring.data.redis.host", redisContainer::getHost);
        r.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }
    @LocalServerPort int port;
    @Autowired AssetRepository assets;
    @Autowired TransactionRepository transactions;
    @Autowired PortfolioRepository portfolios;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired QuoteService quotes;
    @Autowired EntityManagerFactory emf;
    @Autowired HikariDataSource dataSource;
    @Autowired StringRedisTemplate redis;
    @Autowired IntegrationProperties properties;
    @MockitoSpyBean MarketDataCache cache;
    final AtomicInteger hits = new AtomicInteger(), misses = new AtomicInteger();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    final JsonMapper json = new JsonMapper();
    final List<Map<String,Object>> measurements = new ArrayList<>();
    String token;

    @Test void measureIsolatedPortfolios() throws Exception {
        doAnswer(inv -> { Object value=inv.callRealMethod(); (value==null?misses:hits).incrementAndGet(); return value; })
                .when(cache).get(anyString());
        var user=users.save(new User("Operational probe","operational@example.test","unused"));
        token=jwt.generateToken(user);
        // Warm the JVM, connection pools and security pipeline before reported scenarios.
        get("/api/v1/portfolios",200);
        for (int size : List.of(0,5,20,50,100)) {
            var portfolio=portfolios.save(new Portfolio("Synthetic "+size,"Isolated operational validation",user));
            var symbols=new ArrayList<String>();
            for(int i=0;i<size;i++) {
                var asset=new Asset(); asset.setTicker("OP"+size+"X"+i); asset.setName("Synthetic "+i);
                asset.setType(AssetType.STOCK); asset.setExchange("B3"); assets.save(asset); symbols.add(asset.getTicker());
                transactions.save(new Transaction(portfolio,asset,TransactionType.BUY,BigDecimal.TEN,BigDecimal.TEN,
                        LocalDate.now(zone).minusDays(20)));
            }
            String base="/api/v1/portfolios/"+portfolio.getId();
            measure(size,"accounting",()->get(base+"/positions",200));
            measure(size,"quotes_cold",()->symbols.forEach(quotes::getCurrentPrice));
            measure(size,"quotes_warm",()->symbols.forEach(quotes::getCurrentPrice));
            measure(size,"market_warm",()->get(base+"/positions/market",200));
            clearTestCache();
            measure(size,"market_cold",()->get(base+"/positions/market",200));
            clearTestCache();
            var paths=List.of("/api/v1/transactions/portfolio/"+portfolio.getId(),base+"/positions/market",
                    "/api/v1/incomes/portfolio/"+portfolio.getId(),base+"/realized-results",base+"/history?period=ONE_MONTH",
                    base+"/benchmark-comparison?period=ONE_MONTH",base+"/returns?period=ALL",
                    base+"/returns?period=ONE_YEAR",base+"/returns?period=ONE_MONTH");
            measure(size,"dashboard_cold",()->parallelGet(paths));
            measure(size,"dashboard_warm",()->parallelGet(paths));
            if(size==100 && !Boolean.getBoolean("operational.skipSlow")) {
                clearTestCache();
                var before=get("/api/v1/transactions/portfolio/"+portfolio.getId(),200);
                // Successful but slow observations expose aggregate budget exhaustion across assets.
                latencyMs=600;
                measure(size,"slow_market_budget",()->get(base+"/positions/market",504));
                latencyMs=20;
                assertThat(get("/api/v1/transactions/portfolio/"+portfolio.getId(),200)).isEqualTo(before);
                assertThat(get(base+"/positions",200)).isNotBlank();
            }
        }
        // A hanging endpoint verifies actual socket timeout/retries, not just mocked exceptions.
        if(!Boolean.getBoolean("operational.skipSlow")) {
            clearTestCache(); latencyMs=5000;
            measure(1,"read_timeout_retries",()->get("/api/v1/quotes/OP5X0",504));
            latencyMs=20;
        }
        Files.createDirectories(Path.of("target/operational"));
        Files.writeString(Path.of("target/operational/performance.json"),json.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("measurements",measurements,"policy",Map.of(
                        "requestBudgetMs",properties.getRequestBudgetMs(),"readTimeoutMs",properties.getBrapi().getReadTimeoutMs(),
                        "operationTimeoutMs",properties.getBrapi().getOperationTimeoutMs(),"maxAttempts",properties.getBrapi().getMaxAttempts(),
                        "maxConcurrent",properties.getBrapi().getMaxConcurrent(),"quoteTtlSeconds",properties.getQuoteTtlSeconds(),
                        "historyTtlSeconds",properties.getHistoryTtlSeconds()))));
    }
    private void clearTestCache() { var keys=redis.keys("*"); if(keys!=null&&!keys.isEmpty()) redis.delete(keys); }
    private void measure(int size,String phase,Runnable action) {
        httpCalls.set(0); peak.set(0); connections.clear(); requests.clear(); callsByPath.clear(); hits.set(0); misses.set(0);
        var stats=emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        long start=System.nanoTime(); action.run();
        var row=new LinkedHashMap<String,Object>(); row.put("assets",size); row.put("phase",phase);
        row.put("elapsedMs",Duration.ofNanos(System.nanoTime()-start).toMillis()); row.put("httpCalls",httpCalls.get());
        row.put("cacheHits",hits.get()); row.put("cacheMisses",misses.get()); row.put("sqlStatements",stats.getPrepareStatementCount());
        row.put("upstreamPeak",peak.get()); row.put("upstreamConnections",connections.size());
        row.put("uniqueHttpRequests",requests.size());
        row.put("poolTotal",dataSource.getHikariPoolMXBean().getTotalConnections());
        row.put("poolActiveAfter",dataSource.getHikariPoolMXBean().getActiveConnections());
        row.put("callsByPath",new TreeMap<>(callsByPath).entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,e->e.getValue().get())));
        measurements.add(row); System.out.println("LOCAL_PROBE "+row);
        if(phase.endsWith("warm")) assertThat(httpCalls.get()).isZero();
        if(phase.equals("dashboard_cold")) assertThat(httpCalls.get()).isEqualTo(requests.size());
        if(phase.equals("slow_market_budget")) {
            assertThat((long)row.get("elapsedMs")).isBetween(properties.getRequestBudgetMs()-1500,properties.getRequestBudgetMs()+5000);
            assertThat(httpCalls.get()).isLessThan(size);
        }
        if(phase.equals("read_timeout_retries")) {
            assertThat(httpCalls.get()).isEqualTo(3);
            assertThat((long)row.get("elapsedMs")).isLessThan(properties.getBrapi().getOperationTimeoutMs()+3000);
        }
    }
    private String get(String path,int expected) {
        try {
            var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                    .header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s: %s",path,expected==200&&response.statusCode()!=200?response.body():"").isEqualTo(expected);
            if(expected!=200) assertThat(response.body()).doesNotContain("currentValue","totalReturn");
            return response.body();
        } catch(Exception e) {throw new RuntimeException(e);}
    }
    private void parallelGet(List<String> paths) {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=paths.stream().map(p->pool.submit(()->get(p,200))).toList();
            for(var task:tasks) task.get(60,TimeUnit.SECONDS);
        } catch(Exception e) {throw new RuntimeException(e);}
    }
    static HttpServer startServer() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/",OperationalPerformanceProbe::respond); server.start(); return server;
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
    static void respond(HttpExchange exchange) {
        httpCalls.incrementAndGet(); peak.accumulateAndGet(active.incrementAndGet(),Math::max);
        connections.add(exchange.getRemoteAddress().toString());
        requests.add(exchange.getRequestURI().toString());
        callsByPath.computeIfAbsent(exchange.getRequestURI().getPath(),p->new AtomicInteger()).incrementAndGet();
        try {
            Thread.sleep(latencyMs);
            var query=new HashMap<String,String>();
            String raw=exchange.getRequestURI().getRawQuery();
            if(raw!=null) for(String part:raw.split("&")) {var kv=part.split("=",2);query.put(kv[0],URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}
            String path=exchange.getRequestURI().getPath(),symbol=query.getOrDefault("symbols","^BVSP"),body;
            if(path.contains("bcdata")) {
                var f=DateTimeFormatter.ofPattern("dd/MM/uuuu");
                var from=LocalDate.parse(query.get("dataInicial"),f);var to=LocalDate.parse(query.get("dataFinal"),f);
                body=from.datesUntil(to.plusDays(1)).filter(OperationalPerformanceProbe::weekday)
                        .map(d->"{\"data\":\""+d.format(f)+"\",\"valor\":\"0.05\"}").collect(java.util.stream.Collectors.joining(",","[","]"));
            } else if(path.equals("/api/v2/stocks/quote")) {
                body="{\"results\":[{\"symbol\":\""+symbol+"\",\"data\":{\"currency\":\"BRL\",\"regularMarketPrice\":11}}]}";
            } else {
                LocalDate today=LocalDate.now(ZoneId.of("America/Sao_Paulo"));
                String points=today.minusDays(400).datesUntil(today.plusDays(1)).filter(OperationalPerformanceProbe::weekday)
                        .map(d->"{\"date\":"+d.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toEpochSecond()+",\"close\":10}")
                        .collect(java.util.stream.Collectors.joining(",","[","]"));
                body=path.contains("v2")?"{\"results\":[{\"symbol\":\""+symbol+"\",\"changed\":false,\"data\":{\"historicalDataPrice\":"+points+"}}]}":
                        "{\"results\":[{\"symbol\":\"^BVSP\",\"historicalDataPrice\":"+points+"}]}";
            }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
        } catch(Exception ignored) { /* Client cancellation is expected in the timeout scenario. */ }
        finally {active.decrementAndGet();exchange.close();}
    }
    static boolean weekday(LocalDate date) {return date.getDayOfWeek()!=DayOfWeek.SATURDAY&&date.getDayOfWeek()!=DayOfWeek.SUNDAY;}
    @AfterAll static void stopServer() {upstream.stop(0);}
}
