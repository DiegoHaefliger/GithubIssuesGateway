package com.trade.triage.orchestrator.job;

public enum JobEnqueueOutcome {
    ENFILEIRADO,
    JA_EXISTE_JOB_ATIVO,
    CARD_DESCONHECIDO,
    PROJETO_FORA_DO_REGISTRO,
    SUPRIMIDO_POR_KILL_SWITCH
}
