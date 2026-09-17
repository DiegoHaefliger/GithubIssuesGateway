package com.trade.triage.gateway.service;

public enum IngestOutcome {
    CARD_CRIADO,
    DEDUPLICADO,
    REGRESSAO_REABERTA,
    STORM,
    SUPRIMIDO_POR_KILL_SWITCH,
    SUPRIMIDO_POR_INCIDENTE,
    LOG_ORFAO,
    IGNORADO
}
