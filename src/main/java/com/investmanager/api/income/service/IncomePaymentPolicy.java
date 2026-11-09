package com.investmanager.api.income.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class IncomePaymentPolicy {
    private final Clock clock;

    @Autowired
    public IncomePaymentPolicy() {
        this(Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    public IncomePaymentPolicy(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
