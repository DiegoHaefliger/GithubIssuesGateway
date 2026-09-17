package com.trade.triage.orchestrator.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ProposedTest(
        @JsonProperty("arquivo") @NotBlank String arquivo,
        @JsonProperty("identificador") @NotBlank String identificador) {
}
