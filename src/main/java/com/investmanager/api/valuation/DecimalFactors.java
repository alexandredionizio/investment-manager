package com.investmanager.api.valuation;

import java.math.*;

final class DecimalFactors {
    static final MathContext MC = MathContext.DECIMAL128;
    private DecimalFactors() {}
    static BigDecimal power(BigDecimal base, int numerator, int denominator) {
        if (base.signum()<=0 || numerator<0 || denominator<=0) throw new IllegalArgumentException("Fator inválido");
        if (numerator==0) return BigDecimal.ONE;
        // Newton's nth root, entirely decimal; no binary floating point financial calculation.
        BigDecimal root=BigDecimal.ONE, n=BigDecimal.valueOf(denominator);
        for (int i=0;i<200;i++) {
            BigDecimal next=root.multiply(n.subtract(BigDecimal.ONE),MC)
                    .add(base.divide(root.pow(denominator-1,MC),MC),MC).divide(n,MC);
            if (next.subtract(root).abs().compareTo(new BigDecimal("1E-30"))<0) return next.pow(numerator,MC);
            root=next;
        }
        throw new ValuationUnavailableException("Não foi possível calcular o fator com precisão");
    }
}
