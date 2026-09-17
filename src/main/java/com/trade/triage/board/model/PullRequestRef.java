package com.trade.triage.board.model;

public record PullRequestRef(String repositorio, int numero, String url) {

    public String asString() {
        return repositorio + "#" + numero;
    }
}
