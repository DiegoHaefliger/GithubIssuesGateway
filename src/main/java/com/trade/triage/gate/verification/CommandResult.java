package com.trade.triage.gate.verification;

public record CommandResult(int codigoDeSaida, String saida) {

    public boolean sucesso() {
        return codigoDeSaida == 0;
    }
}
