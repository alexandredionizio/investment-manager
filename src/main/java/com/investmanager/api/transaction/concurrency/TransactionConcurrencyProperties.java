package com.investmanager.api.transaction.concurrency;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "investment.transactions.concurrency")
public class TransactionConcurrencyProperties {

    @Min(1)
    private long lockTimeoutMs = 2000;
    @Min(1)
    private int maxAttempts = 3;
    @Min(1)
    private long initialBackoffMs = 100;
    @Min(1)
    private long maxBackoffMs = 500;

    public long getLockTimeoutMs() { return lockTimeoutMs; }
    public void setLockTimeoutMs(long lockTimeoutMs) { this.lockTimeoutMs = lockTimeoutMs; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public void setInitialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }
    public long getMaxBackoffMs() { return maxBackoffMs; }
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }

    @AssertTrue(message = "max-backoff-ms deve ser maior ou igual a initial-backoff-ms")
    public boolean isBackoffRangeValid() {
        return maxBackoffMs >= initialBackoffMs;
    }
}
