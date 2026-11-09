package com.investmanager.api.income.service;

import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.mapper.IncomeMapper;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class IncomePaymentPolicyTest {
    @ParameterizedTest @CsvSource({"2026-10-30,true", "2026-10-31,true", "2026-11-01,false"})
    void classifiesPaymentUsingBrazilDateAndPreservesAmounts(String paymentDate, boolean received) {
        // UTC is already November, but the application's business date is still October 31.
        IncomePaymentPolicy policy = new IncomePaymentPolicy(Clock.fixed(
                Instant.parse("2026-11-01T01:30:00Z"), ZoneId.of("America/Sao_Paulo")));
        Income income = new Income(null, null, IncomeType.DIVIDEND, new BigDecimal("0.12345678"),
                new BigDecimal("10.125"), LocalDate.of(2026, 9, 10), LocalDate.parse(paymentDate));
        var response = Mappers.getMapper(IncomeMapper.class).toResponse(income, policy.today());
        assertEquals(LocalDate.of(2026, 10, 31), policy.today());
        assertEquals(received, response.received());
        assertEquals(income.getQuantity(), response.quantity());
        assertEquals(new BigDecimal("1.24999989750"), response.totalAmount());
    }
}
