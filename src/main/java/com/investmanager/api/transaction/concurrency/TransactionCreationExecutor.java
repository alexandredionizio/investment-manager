package com.investmanager.api.transaction.concurrency;

import com.investmanager.api.transaction.exception.TransactionConcurrencyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class TransactionCreationExecutor {

    private final TransactionTemplate transactionTemplate;
    private final TransactionConcurrencyProperties properties;

    public TransactionCreationExecutor(PlatformTransactionManager transactionManager,
                                       TransactionConcurrencyProperties properties) {
        this.properties = properties;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(Supplier<T> creation) {
        long backoff = properties.getInitialBackoffMs();
        for (int attempt = 1; ; attempt++) {
            try {
                // execute só retorna após commit; uma falha sai daqui após rollback.
                return transactionTemplate.execute(status -> creation.get());
            } catch (PortfolioAssetLockAcquisitionException exception) {
                if (attempt >= properties.getMaxAttempts()) {
                    throw new TransactionConcurrencyException(exception);
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new TransactionConcurrencyException(interrupted);
                }
                long maximum = properties.getMaxBackoffMs();
                backoff = backoff > maximum / 2 ? maximum : Math.min(backoff * 2, maximum);
            }
        }
    }
}
