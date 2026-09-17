package com.trade.triage.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record ProjectEntry(
        @JsonProperty("projeto") @NotBlank String projeto,
        @JsonProperty("diretorio") @NotBlank String diretorio,
        @JsonProperty("servicos") @NotEmpty List<String> servicos,
        @JsonProperty("pacotes_raiz") @NotEmpty List<String> pacotesRaiz,
        @JsonProperty("repositorio") @NotBlank String repositorio,
        @JsonProperty("branch_base") @NotBlank String branchBase,
        @JsonProperty("comando_de_teste") @NotEmpty List<String> comandoDeTeste,
        @JsonProperty("ambientes") @NotEmpty List<String> ambientes,
        @JsonProperty("board") @NotBlank String board,
        @JsonProperty("allowlist") @NotNull List<String> allowlist,
        @JsonProperty("blast_radius") @NotNull Map<String, BlastRadius> blastRadius,
        @JsonProperty("limites") @NotNull @Valid ProjectLimits limites,
        @JsonProperty("ativo") boolean ativo) {

    public ProjectEntry {
        servicos = servicos == null ? List.of() : List.copyOf(servicos);
        pacotesRaiz = pacotesRaiz == null ? List.of() : List.copyOf(pacotesRaiz);
        ambientes = ambientes == null ? List.of() : List.copyOf(ambientes);
        allowlist = allowlist == null ? List.of() : List.copyOf(allowlist);
        comandoDeTeste = comandoDeTeste == null ? List.of() : List.copyOf(comandoDeTeste);
        blastRadius = blastRadius == null ? Map.of() : Map.copyOf(blastRadius);
        limites = limites == null ? ProjectLimits.conservador() : limites;
    }

    public boolean atendeServico(String service) {
        return servicos.contains(service);
    }

    public boolean monitoraAmbiente(String env) {
        return ambientes.contains(env);
    }
}
