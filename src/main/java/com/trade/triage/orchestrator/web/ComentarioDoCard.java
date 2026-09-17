package com.trade.triage.orchestrator.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ComentarioDoCard(
        @JsonProperty("autor") String autor,
        @JsonProperty("tipo_de_autor") String tipoDeAutor,
        @JsonProperty("criado_em") Instant criadoEm,
        @JsonProperty("corpo") String corpo) {
}
