package com.trade.triage.board.model;

import java.time.Instant;

public record CardComment(String autor, String tipoDeAutor, Instant criadoEm, String corpo) {

    public boolean deBot() {
        return "Bot".equalsIgnoreCase(tipoDeAutor);
    }
}
