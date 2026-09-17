package com.trade.triage.board.model;

public record CardRef(String repositorio, int numero) {

    public String asString() {
        return repositorio + "#" + numero;
    }

    public static CardRef parse(String valor) {
        int separador = valor.lastIndexOf('#');
        if (separador < 0) {
            throw new IllegalArgumentException("Referencia de card invalida: " + valor);
        }
        return new CardRef(valor.substring(0, separador), Integer.parseInt(valor.substring(separador + 1)));
    }
}
