package com.trade.triage.gateway.service;

import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.gateway.web.GrafanaAlert;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class ErrorSignalExtractor {

    private final Clock clock;

    public ErrorSignalExtractor(Clock clock) {
        this.clock = clock;
    }

    public ErrorSignal extract(GrafanaAlert alerta) {
        return new ErrorSignal(
                alerta.label("service"),
                alerta.label("env"),
                alerta.label("rule_id"),
                alerta.label("severity"),
                alerta.annotation("trace_id"),
                alerta.annotation("logger"),
                alerta.annotation("exception_class"),
                alerta.annotation("stacktrace"),
                alerta.annotation("message"),
                momento(alerta));
    }

    private Instant momento(GrafanaAlert alerta) {
        return alerta.startsAt() == null ? clock.instant() : alerta.startsAt();
    }
}
