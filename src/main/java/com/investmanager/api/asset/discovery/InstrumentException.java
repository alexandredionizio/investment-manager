package com.investmanager.api.asset.discovery;
public class InstrumentException extends RuntimeException {
    private final int status;
    private final String code;
    public InstrumentException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static InstrumentException ambiguous() { return new InstrumentException(409, "IDENTITY_REVIEW_REQUIRED", "Identificação ambígua ou evento societário: o ativo exige revisão antes de uma nova compra."); }
    public static InstrumentException ineligible() { return new InstrumentException(422, "INSTRUMENT_INELIGIBLE", "Instrumento inexistente, inativo ou incompatível com este fluxo de compra."); }
    public static InstrumentException invalid() { return new InstrumentException(400, "INVALID_INSTRUMENT_QUERY", "Informe ticker/nome com 2 a 80 caracteres, página de 1 a 1000 e limite de 1 a 20."); }
}
