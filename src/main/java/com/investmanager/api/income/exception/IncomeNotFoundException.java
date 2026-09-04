package com.investmanager.api.income.exception;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

public class IncomeNotFoundException extends RuntimeException {

    public IncomeNotFoundException(Long id) {
        super("Provento não encontrado com o ID: " + id);
    }
}
