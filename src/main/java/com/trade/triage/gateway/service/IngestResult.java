package com.trade.triage.gateway.service;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IngestResult(
        @JsonProperty("resultado") IngestOutcome resultado,
        @JsonProperty("fingerprint") String fingerprint,
        @JsonProperty("card") String cardRef,
        @JsonProperty("motivo") String motivo) {

    public static IngestResult de(IngestOutcome resultado, String motivo) {
        return new IngestResult(resultado, null, null, motivo);
    }

    public static IngestResult comCard(IngestOutcome resultado, String fingerprint, String cardRef) {
        return new IngestResult(resultado, fingerprint, cardRef, null);
    }
}
