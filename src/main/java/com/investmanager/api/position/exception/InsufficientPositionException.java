package com.investmanager.api.position.exception;

public class InsufficientPositionException extends RuntimeException {

    public InsufficientPositionException(String ticker) {
        super("Posição insuficiente para realizar a venda do ativo: " + ticker);
    }
}
