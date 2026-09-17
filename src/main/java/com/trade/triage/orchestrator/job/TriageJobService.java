package com.trade.triage.orchestrator.job;

public interface TriageJobService {

    JobEnqueueResult enfileirar(String cardRef, String eventoOrigem);

    void marcarPronto(String jobId);

    void marcarFalha(String jobId, String motivo);
}
