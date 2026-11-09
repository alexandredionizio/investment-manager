package com.investmanager.api.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record ValuationTerms(
        @NotNull Instrument instrument,
        RateType rateType,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4, fraction=8) BigDecimal annualRate,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4, fraction=8) BigDecimal cdiPercent,
        DayCount dayCount,
        LocalDate applicationDate,
        @NotNull LocalDate maturityDate,
        @Min(1) @Max(2) Integer ipcaLagMonths,
        LocalDate conversionDate,
        Boolean brazilResidentIndividual,
        Boolean bulletPaymentConfirmed) {
    public enum Instrument { CDB, LCI, LCA, TESOURO_SELIC, TESOURO_PREFIXADO, TESOURO_PREFIXADO_JUROS,
        TESOURO_IPCA, TESOURO_IPCA_JUROS, TESOURO_RENDA }
    public enum RateType { PRE_FIXED, CDI_PERCENT, IPCA_PLUS }
    public enum DayCount { BUSINESS_252, ACT_365 }
    public boolean treasury() { return instrument != null && instrument.name().startsWith("TESOURO_"); }
    public void validate() {
        if (instrument == null || maturityDate == null) invalid("Informe o título e o vencimento");
        if (applicationDate != null && !applicationDate.isBefore(maturityDate)) invalid("Aplicação deve preceder o vencimento");
        if (treasury()) {
            if (instrument == Instrument.TESOURO_RENDA && (conversionDate == null
                    || !conversionDate.isBefore(maturityDate)
                    || !conversionDate.plusMonths(239).equals(maturityDate)))
                invalid("RendA+ exige conversão e vencimento da 240ª parcela (239 meses após conversão)");
        } else {
            if (!Boolean.TRUE.equals(bulletPaymentConfirmed)) invalid("Confirme pagamento único no vencimento; cupons e amortizações intermediárias não são suportados neste contrato");
            if (applicationDate == null || rateType == null || dayCount == null) invalid("Informe aplicação, indexador e convenção");
            if (applicationDate.getYear() < 2001 || maturityDate.getYear() > 2099) invalid("Calendário suportado: 2001 a 2099");
            if (rateType == RateType.CDI_PERCENT) {
                if (cdiPercent == null || dayCount != DayCount.BUSINESS_252) invalid("CDI exige percentual e dias úteis/252");
            } else if (annualRate == null) invalid("Informe a taxa anual contratada");
            if (rateType == RateType.IPCA_PLUS && (ipcaLagMonths == null || applicationDate.getDayOfMonth() > 28))
                invalid("IPCA exige defasagem e aniversário entre os dias 1 e 28");
        }
    }
    private static void invalid(String reason) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, reason); }
}
