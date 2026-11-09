package com.investmanager.api.shared.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("investment.integrations")
public class IntegrationProperties {
    @Valid private Policy brapi = new Policy();
    @Valid private Policy bcb = new Policy();
    @Valid private Policy coingecko = new Policy();
    @Valid private Policy treasury = new Policy();
    public Policy getCoingecko() { return coingecko; }
    public void setCoingecko(Policy value) { coingecko = value; }
    public Policy getTreasury() { return treasury; }
    public void setTreasury(Policy value) { treasury = value; }
    public Policy policy(String provider) {
        return switch (provider) {
            case "brapi" -> brapi; case "bcb" -> bcb;
            case "coingecko" -> coingecko; case "treasury" -> treasury;
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }
    @Min(1) private long requestBudgetMs = 45000;
    @Min(1) private long quoteTtlSeconds = 300;
    @Min(1) private long historyTtlSeconds = 900;
    public Policy getBrapi() { return brapi; }
    public void setBrapi(Policy value) { brapi = value; }
    public Policy getBcb() { return bcb; }
    public void setBcb(Policy value) { bcb = value; }
    public long getRequestBudgetMs() { return requestBudgetMs; }
    public void setRequestBudgetMs(long value) { requestBudgetMs = value; }
    public long getQuoteTtlSeconds() { return quoteTtlSeconds; }
    public void setQuoteTtlSeconds(long value) { quoteTtlSeconds = value; }
    public long getHistoryTtlSeconds() { return historyTtlSeconds; }
    public void setHistoryTtlSeconds(long value) { historyTtlSeconds = value; }

    public static class Policy {
        @Min(1) private long connectTimeoutMs = 2000;
        @Min(1) private long readTimeoutMs = 4000;
        @Min(1) private long operationTimeoutMs = 15000;
        @Min(1) private int maxAttempts = 3;
        @Min(1) private long initialBackoffMs = 250;
        @Min(1) private long maxBackoffMs = 2000;
        @Min(1) private int maxConcurrent = 1;
        public long getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(long v) { connectTimeoutMs = v; }
        public long getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(long v) { readTimeoutMs = v; }
        public long getOperationTimeoutMs() { return operationTimeoutMs; }
        public void setOperationTimeoutMs(long v) { operationTimeoutMs = v; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { maxAttempts = v; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public void setInitialBackoffMs(long v) { initialBackoffMs = v; }
        public long getMaxBackoffMs() { return maxBackoffMs; }
        public void setMaxBackoffMs(long v) { maxBackoffMs = v; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int v) { maxConcurrent = v; }
    }
}
