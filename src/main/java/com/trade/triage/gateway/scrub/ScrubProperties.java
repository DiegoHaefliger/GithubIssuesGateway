package com.trade.triage.gateway.scrub;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "triage.scrub")
public record ScrubProperties(List<String> camposSensiveis, List<String> camposConhecidos) {

    private static final List<String> CAMPOS_SENSIVEIS_PADRAO = List.of(
            "password", "senha", "secret", "token", "apikey", "api_key", "authorization",
            "credential", "credencial", "private_key", "passphrase", "cookie", "session",
            "account", "conta", "cpf", "cnpj", "email", "phone", "telefone");

    private static final List<String> CAMPOS_CONHECIDOS_PADRAO = List.of(
            "timestamp", "level", "service", "env", "trace_id", "span_id", "logger", "class",
            "thread", "exception", "exception_class", "stacktrace", "message", "rule_id", "severity");

    public ScrubProperties {
        camposSensiveis = camposSensiveis == null || camposSensiveis.isEmpty()
                ? CAMPOS_SENSIVEIS_PADRAO : List.copyOf(camposSensiveis);
        camposConhecidos = camposConhecidos == null || camposConhecidos.isEmpty()
                ? CAMPOS_CONHECIDOS_PADRAO : List.copyOf(camposConhecidos);
    }
}
