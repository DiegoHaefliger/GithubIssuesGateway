package com.trade.triage.board.model;

public record PullRequestRef(String repositorio, int numero, String url, String nodeId) {

    public String asString() {
        return repositorio + "#" + numero;
    }
}
