package com.trade.triage.orchestrator.job;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JobEnqueueResult(
        @JsonProperty("resultado") JobEnqueueOutcome resultado,
        @JsonProperty("job") String jobId,
        @JsonProperty("motivo") String motivo) {

    public static JobEnqueueResult enfileirado(String jobId) {
        return new JobEnqueueResult(JobEnqueueOutcome.ENFILEIRADO, jobId, null);
    }

    public static JobEnqueueResult recusado(JobEnqueueOutcome resultado, String motivo) {
        return new JobEnqueueResult(resultado, null, motivo);
    }
}
