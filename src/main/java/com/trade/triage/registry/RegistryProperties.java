package com.trade.triage.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "triage.registry")
public record RegistryProperties(
        Origem origem,
        String arquivo,
        String url,
        Duration recargaIntervalo,
        Duration timeout) {

    public enum Origem {
        ARQUIVO,
        HTTP
    }

    public RegistryProperties {
        origem = origem == null ? Origem.ARQUIVO : origem;
        arquivo = arquivo == null ? "config/projetos.json" : arquivo;
        recargaIntervalo = recargaIntervalo == null ? Duration.ofMinutes(5) : recargaIntervalo;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }
}
