package com.trade.triage.orchestrator.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TriageResult(
        @JsonProperty("job_id") @NotBlank String jobId,
        @JsonProperty("card") @NotBlank String cardRef,
        @JsonProperty("hipotese") @NotBlank String hipotese,
        @JsonProperty("evidencia_a_favor") @NotEmpty List<String> evidenciaAFavor,
        @JsonProperty("evidencia_contra") @NotEmpty List<String> evidenciaContra,
        @JsonProperty("diff") String diff,
        @JsonProperty("teste_novo") @Valid ProposedTest testeNovo,
        @JsonProperty("justificativa") @NotBlank String justificativa,
        @JsonProperty("pergunta_que_destrava") String perguntaQueDestrava) {

    public TriageResult {
        evidenciaAFavor = evidenciaAFavor == null ? List.of() : List.copyOf(evidenciaAFavor);
        evidenciaContra = evidenciaContra == null ? List.of() : List.copyOf(evidenciaContra);
    }

    public boolean temProposta() {
        return diff != null && !diff.isBlank() && testeNovo != null;
    }
}
