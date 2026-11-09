package com.investmanager.api.transaction.concurrency;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

@Component
public class PortfolioAssetLockManager {

    private final PortfolioAssetLockRepository repository;
    private final EntityManager entityManager;
    private final TransactionConcurrencyProperties properties;

    public PortfolioAssetLockManager(PortfolioAssetLockRepository repository, EntityManager entityManager,
                                     TransactionConcurrencyProperties properties) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long portfolioId, Long assetId) {
        String previousTimeout = (String) entityManager
                .createNativeQuery("select current_setting('lock_timeout')").getSingleResult();
        setLockTimeout(properties.getLockTimeoutMs() + "ms");

        try {
            // O primeiro INSERT também pode aguardar outra transação criando o mesmo par.
            repository.ensureExists(portfolioId, assetId);
            repository.findForUpdate(new PortfolioAssetLockId(portfolioId, assetId))
                    .orElseThrow(() -> new IllegalStateException("Registro de coordenação não encontrado"));
        } catch (RuntimeException exception) {
            if (isRetryableLockFailure(exception)) {
                throw new PortfolioAssetLockAcquisitionException(exception);
            }
            throw exception;
        }

        // O timeout é local à transação e restrito à aquisição. O lock segue ativo até seu fim.
        setLockTimeout(previousTimeout);
    }

    private void setLockTimeout(String value) {
        entityManager.createNativeQuery("select set_config('lock_timeout', :value, true)")
                .setParameter("value", value)
                .getSingleResult();
    }

    static boolean isRetryableLockFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                for (SQLException sql = sqlException; sql != null; sql = sql.getNextException()) {
                    if ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
