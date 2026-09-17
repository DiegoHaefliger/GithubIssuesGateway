package com.trade.triage.orchestrator.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RunnerSignalRequest(
        @JsonProperty("conclusao") @NotBlank String conclusao,
        @JsonProperty("motivo") String motivo) {

    public boolean concluiuComSucesso() {
        return "success".equalsIgnoreCase(conclusao);
    }
}
