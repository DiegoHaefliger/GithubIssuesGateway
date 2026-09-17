package com.trade.triage.gate.verification;

public record FileDiff(String caminho, String texto, int linhasAdicionadas, int linhasRemovidas) {

    public int linhasAlteradas() {
        return linhasAdicionadas + linhasRemovidas;
    }
}
