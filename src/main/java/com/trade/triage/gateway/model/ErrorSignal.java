package com.trade.triage.gateway.model;

import java.time.Instant;
import java.util.Optional;

public record ErrorSignal(
        String service,
        String env,
        String ruleId,
        String severity,
        String traceId,
        String loggerName,
        String exceptionClass,
        String stacktrace,
        String message,
        String painelUrl,
        Instant occurredAt,
        String localizacao) {

    public Optional<String> traceIdOpcional() {
        return Optional.ofNullable(traceId).filter(value -> !value.isBlank());
    }

    /**
     * Onde o defeito mora, quando a fonte sabe dizer sem depender do stacktrace. O front manda
     * {@code rota@componente}: o bundle e' minificado e o frame muda de posicao a cada build, entao
     * frame como chave geraria card novo do mesmo bug a cada deploy.
     */
    public Optional<String> localizacaoOpcional() {
        return Optional.ofNullable(localizacao).filter(value -> !value.isBlank());
    }
}
