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
        Instant occurredAt) {

    public Optional<String> traceIdOpcional() {
        return Optional.ofNullable(traceId).filter(value -> !value.isBlank());
    }
}
