package com.trade.triage.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ProjectRegistrySnapshot(
        @JsonProperty("versao") @NotBlank String versao,
        @JsonProperty("projetos") @NotNull @Valid List<ProjectEntry> projetos,
        @JsonProperty("carregado_em") Instant carregadoEm) {

    public ProjectRegistrySnapshot {
        projetos = projetos == null ? List.of() : List.copyOf(projetos);
        carregadoEm = carregadoEm == null ? Instant.now() : carregadoEm;
    }

    public Optional<ProjectEntry> findByServiceAndEnv(String service, String env) {
        return projetos.stream()
                .filter(ProjectEntry::ativo)
                .filter(entry -> entry.atendeServico(service))
                .filter(entry -> entry.monitoraAmbiente(env))
                .findFirst();
    }

    public Optional<ProjectEntry> findByRepository(String repositorio) {
        return projetos.stream()
                .filter(entry -> entry.repositorio().equalsIgnoreCase(repositorio))
                .findFirst();
    }

    public static ProjectRegistrySnapshot vazio() {
        return new ProjectRegistrySnapshot("vazio", List.of(), Instant.EPOCH);
    }
}
