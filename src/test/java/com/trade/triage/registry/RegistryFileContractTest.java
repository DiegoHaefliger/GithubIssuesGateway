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
        assertThat(registry.snapshot().projetos()).isNotEmpty();
        registry.snapshot().projetos().forEach(projeto -> projeto.servicos().forEach(servico ->
                projeto.ambientes().forEach(ambiente -> {
                    if (projeto.ativo()) {
                        assertThat(registry.findByServiceAndEnv(servico, ambiente)).contains(projeto);
                    } else {
                        assertThat(registry.findByServiceAndEnv(servico, ambiente)).isEmpty();
                    }
                })));
    }
}
