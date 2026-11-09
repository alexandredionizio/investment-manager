package com.investmanager.api.transaction.concurrency;

/** Identifica exclusivamente falhas transitórias ocorridas ao adquirir a coordenação. */
public class PortfolioAssetLockAcquisitionException extends RuntimeException {

    public PortfolioAssetLockAcquisitionException(Throwable cause) {
        super("Falha transitória ao adquirir o lock de carteira e ativo", cause);
    }
}
