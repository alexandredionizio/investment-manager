package com.investmanager.api.portfolio.exception;

public class PortfolioNotFoundException extends RuntimeException {

    public PortfolioNotFoundException(Long id) {
        super("Carteira não encontrada com o id: " + id);
    }
}
