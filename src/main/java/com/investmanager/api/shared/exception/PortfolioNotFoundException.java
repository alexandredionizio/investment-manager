package com.investmanager.api.shared.exception;

public class PortfolioNotFoundException extends RuntimeException {

    public PortfolioNotFoundException(Long id) {
        super("Portfolio not found with id: " + id);
    }
}
