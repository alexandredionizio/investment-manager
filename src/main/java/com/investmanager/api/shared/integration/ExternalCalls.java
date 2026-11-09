package com.investmanager.api.shared.integration;

import com.investmanager.api.quote.exception.QuoteNotFoundException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import javax.net.ssl.SSLException;
import java.net.*;
import java.net.http.HttpTimeoutException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Single retry boundary for idempotent provider GETs. No financial/database operations run here. */
@Component
public class ExternalCalls {
    private static final Logger log = LoggerFactory.getLogger(ExternalCalls.class);
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Semaphore> gates;
    private final IntegrationProperties properties;
    public ExternalCalls(IntegrationProperties properties) {
        this.properties = properties;
        gates = Map.of("brapi", new Semaphore(properties.getBrapi().getMaxConcurrent(), true),
                "bcb", new Semaphore(properties.getBcb().getMaxConcurrent(), true),
                "coingecko", new Semaphore(properties.getCoingecko().getMaxConcurrent(), true),
                "treasury", new Semaphore(properties.getTreasury().getMaxConcurrent(), true));
    }
    @PreDestroy public void close() { workers.shutdownNow(); }

    public <T> T get(String provider, String operation, Supplier<T> request) {
        var policy = properties.policy(provider);
        long started = System.nanoTime();
        long remaining = IntegrationBudgetFilter.remainingNanos(TimeUnit.MILLISECONDS.toNanos(policy.getOperationTimeoutMs()));
        AtomicInteger attempts = new AtomicInteger();
        String outcome = "success";
        String category = "none";
        Future<T> task = null;
        try {
            if (remaining <= 0) throw new ExternalIntegrationException(504, "request_budget");
            long deadline = started + remaining;
            task = workers.submit(() -> attempt(provider, operation, policy, deadline, attempts, request));
            T result = task.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (result instanceof Collection<?> values && values.isEmpty()) outcome = "no_data";
            return result;
        } catch (TimeoutException e) {
            outcome = "failure"; category = "operation_timeout";
            throw new ExternalIntegrationException(504, category);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "failure"; category = "interrupted";
            throw new ExternalIntegrationException(503, category);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof QuoteNotFoundException missing) {
                outcome = "no_data"; category = "404"; throw missing;
            }
            outcome = "failure";
            if (e.getCause() instanceof Error error) throw error;
            ExternalIntegrationException failure = classify(e.getCause());
            category = failure.category();
            throw failure;
        } catch (ExternalIntegrationException e) {
            outcome = "failure"; category = e.category(); throw e;
        } finally {
            if (task != null && !task.isDone()) task.cancel(true);
            log.info("integration={} operation={} attempts={} elapsedMs={} outcome={} category={}",
                    provider, operation, attempts.get(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), outcome, category);
        }
    }

    private <T> T attempt(String provider, String operation, IntegrationProperties.Policy policy, long deadline,
                          AtomicInteger attempts, Supplier<T> request) throws InterruptedException {
        for (int number = 1; ; number++) {
            Semaphore gate = gates.get(provider);
            if (!gate.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                throw new ExternalIntegrationException(504, "capacity_timeout");
            RuntimeException failure;
            try {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
                    throw new ExternalIntegrationException(504, "operation_timeout");
                attempts.incrementAndGet();
                return request.get();
            } catch (RuntimeException e) { failure = e; }
            finally { gate.release(); }
            if (number >= policy.getMaxAttempts() || !transientFailure(failure)) throw failure;
            long exponential = (long) Math.min(policy.getMaxBackoffMs(),
                    policy.getInitialBackoffMs() * Math.pow(2, Math.min(number - 1, 30)));
            long delay = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 2), Math.max(2, exponential + 1));
            delay = Math.max(delay, retryAfterMillis(failure, Instant.now()));
            if (TimeUnit.MILLISECONDS.toNanos(delay) >= deadline - System.nanoTime()) throw failure;
            log.info("integration={} operation={} attempt={} outcome=retry category={} delayMs={}",
                    provider, operation, number, classify(failure).category(), delay);
            // Do not hold a provider slot during backoff, and preserve cancellation.
            Thread.sleep(delay);
        }
    }

    static boolean transientFailure(Throwable failure) {
        if (failure instanceof RestClientResponseException http)
            return switch (http.getStatusCode().value()) { case 429, 502, 503, 504 -> true; default -> false; };
        if (!(failure instanceof ResourceAccessException)) return false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof SSLException || cause instanceof UnknownHostException) return false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof ConnectException || cause instanceof SocketException) return true;
        return false;
    }

    static long retryAfterMillis(Throwable failure, Instant now) {
        if (!(failure instanceof RestClientResponseException http) || http.getResponseHeaders() == null) return 0;
        String value = http.getResponseHeaders().getFirst("Retry-After");
        if (value == null) return 0;
        try {
            // Saturate oversized values: never turn a long server delay into an immediate retry.
            if (value.trim().matches("[0-9]+")) {
                var millis = new java.math.BigInteger(value.trim()).multiply(java.math.BigInteger.valueOf(1000));
                return millis.min(java.math.BigInteger.valueOf(Long.MAX_VALUE / 1_000_000)).longValue();
            }
            return Math.max(0, Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());
        } catch (RuntimeException ignored) { return 0; }
    }

    private static ExternalIntegrationException classify(Throwable failure) {
        if (failure instanceof ExternalIntegrationException e) return e;
        if (failure instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            return new ExternalIntegrationException(status == 504 ? 504 : status == 429 || status == 502 || status == 503 ? 503 : 502,
                    "http_" + status);
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException)
                return new ExternalIntegrationException(504, "timeout");
            if (cause instanceof UnknownHostException) return new ExternalIntegrationException(503, "dns");
            if (cause instanceof SSLException) return new ExternalIntegrationException(502, "tls");
        }
        return new ExternalIntegrationException(failure instanceof ResourceAccessException ? 503 : 502,
                failure instanceof ResourceAccessException ? "connection" : "invalid_payload");
    }
}
