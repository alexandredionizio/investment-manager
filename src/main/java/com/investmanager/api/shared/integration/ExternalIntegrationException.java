package com.investmanager.api.shared.integration;

public class ExternalIntegrationException extends IllegalStateException {
    private final int status;
    private final String category;
    public ExternalIntegrationException(int status, String category) {
        // Never copy provider response bodies, URLs or credentials into public errors.
        super(status == 504 ? "Tempo limite na consulta ao provedor de dados. Tente novamente."
                : "Não foi possível obter dados válidos do provedor. Tente novamente.");
        this.status = status;
        this.category = category;
    }
    public int status() { return status; }
    public String category() { return category; }
}
