package com.investmanager.api.benchmark.exception;

import com.investmanager.api.benchmark.model.BenchmarkType;
import java.time.LocalDate;
import java.util.List;

public class BenchmarkHistoryIncompleteException extends RuntimeException {
    private final List<LocalDate> missingDates;

    public BenchmarkHistoryIncompleteException(BenchmarkType benchmark, List<LocalDate> missingDates) {
        super("Histórico de " + benchmark + " incompleto no período solicitado: "
                + missingDates.size() + " registro(s) ausente(s), a partir de " + missingDates.getFirst() + ".");
        this.missingDates = List.copyOf(missingDates);
    }

    public BenchmarkHistoryIncompleteException(String message) {
        super(message);
        this.missingDates = List.of();
    }

    public List<LocalDate> missingDates() { return missingDates; }
}
