package com.investmanager.api.transaction.concurrency;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.Collection;
@Component
public class InstrumentIdentityLock {
    private final EntityManager em;
    private final TransactionConcurrencyProperties properties;
    public InstrumentIdentityLock(EntityManager em, TransactionConcurrencyProperties properties) { this.em = em; this.properties = properties; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Collection<String> keys) {
        String previous = (String) em.createNativeQuery("select current_setting('lock_timeout')").getSingleResult();
        timeout(properties.getLockTimeoutMs() + "ms");
        try {
            for (String key : keys.stream().distinct().sorted().toList())
                em.createNativeQuery("select true from pg_advisory_xact_lock(hashtextextended(:key, 0))")
                        .setParameter("key", "instrument:" + key).getSingleResult();
        } catch (RuntimeException failure) {
            if (PortfolioAssetLockManager.isRetryableLockFailure(failure)) throw new PortfolioAssetLockAcquisitionException(failure);
            throw failure;
        }
        timeout(previous);
    }
    private void timeout(String value) { em.createNativeQuery("select set_config('lock_timeout', :value, true)").setParameter("value", value).getSingleResult(); }
}
