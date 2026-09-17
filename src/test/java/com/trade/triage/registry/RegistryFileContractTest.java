package com.trade.triage.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trade.triage.registry.source.FileSystemRegistrySource;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryFileContractTest {

    @Test
    void arquivoDeConfiguracaoDoRepositorioEhValido() {
        CachedProjectRegistry registry = new CachedProjectRegistry(
                new FileSystemRegistrySource(Path.of("config/projetos.json")),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new RegistryValidator(Validation.buildDefaultValidatorFactory().getValidator()));

        registry.reload();

        assertThat(registry.version()).isNotEqualTo("vazio");
        assertThat(registry.snapshot().projetos()).hasSize(3);
        assertThat(registry.findByServiceAndEnv("trade-backend", "producao")).isPresent();
        assertThat(registry.findByServiceAndEnv("crypto-monitor", "producao")).isEmpty();
    }
}
