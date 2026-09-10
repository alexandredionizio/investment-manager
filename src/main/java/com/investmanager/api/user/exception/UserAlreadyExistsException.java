package com.investmanager.api.user.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException() {
        super("E-mail já cadastrado");
    }
}