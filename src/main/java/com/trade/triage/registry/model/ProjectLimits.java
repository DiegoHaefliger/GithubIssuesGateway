package com.trade.triage.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

public record ProjectLimits(
        @JsonProperty("diff_max_linhas") @Positive int diffMaxLinhas,
        @JsonProperty("diff_max_arquivos") @Positive int diffMaxArquivos,
        @JsonProperty("cards_por_hora") @Positive int cardsPorHora,
        @JsonProperty("jobs_por_hora") @Positive int jobsPorHora) {

    public static ProjectLimits conservador() {
        return new ProjectLimits(50, 3, 10, 10);
    }
}
