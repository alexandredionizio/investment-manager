package com.investmanager.api.shared.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Redis is an optimization. Only connectivity/timeouts may bypass it. */
@Component
public class MarketDataCache {
    private static final Logger log = LoggerFactory.getLogger(MarketDataCache.class);
    private final StringRedisTemplate redis;
    public MarketDataCache(StringRedisTemplate redis) { this.redis = redis; }
    public String get(String key) {
        try { return redis.opsForValue().get(key); }
        catch (DataAccessResourceFailureException | QueryTimeoutException e) {
            log.warn("integration=redis operation=read outcome=bypass category={}", e.getClass().getSimpleName());
            return null;
        }
    }
    public void put(String key, String value, Duration ttl) {
        try { redis.opsForValue().set(key, value, ttl); }
        catch (DataAccessResourceFailureException | QueryTimeoutException e) {
            log.warn("integration=redis operation=write outcome=bypass category={}", e.getClass().getSimpleName());
        }
    }
    public void invalidEntry() { log.warn("integration=redis operation=read outcome=miss category=invalid_payload"); }
}
