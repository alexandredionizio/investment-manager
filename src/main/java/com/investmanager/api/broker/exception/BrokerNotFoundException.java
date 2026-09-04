package com.investmanager.api.broker.exception;

public class BrokerNotFoundException extends RuntimeException {

    public BrokerNotFoundException(Long id) {
        super("Corretora não encontrada com o ID: " + id);
    }
}
