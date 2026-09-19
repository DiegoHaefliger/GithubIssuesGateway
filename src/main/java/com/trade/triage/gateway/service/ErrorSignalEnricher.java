package com.trade.triage.gateway.service;

import com.trade.triage.gateway.evidence.observability.ObservabilityClient;
import com.trade.triage.gateway.fingerprint.StackFrameSelector;
import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Regra volumetrica so' diz a classe da excecao; o log individual traz stack, mensagem e trace_id. */
@Component
public class ErrorSignalEnricher {

    private static final Duration JANELA_DA_REGRA = Duration.ofMinutes(5);
    private static final Duration TOLERANCIA_DE_INGESTAO = Duration.ofMinutes(1);

    private final ObservabilityClient observability;
    private final StackFrameSelector frameSelector;
    private final Clock clock;

    public ErrorSignalEnricher(ObservabilityClient observability, StackFrameSelector frameSelector, Clock clock) {
        this.observability = observability;
        this.frameSelector = frameSelector;
        this.clock = clock;
    }

    public ErrorSignal enriquecer(ErrorSignal signal, ProjectEntry projeto) {
        Instant momento = signal.occurredAt() == null ? clock.instant() : signal.occurredAt();
        return observability.ultimoErro(signal.service(), signal.env(), signal.exceptionClass(),
                        momento.minus(JANELA_DA_REGRA), momento.plus(TOLERANCIA_DE_INGESTAO))
                .map(erro -> signal.enriquecidoCom(erro.traceId(), erro.mensagem(), erro.stacktrace(),
                        localizacao(erro.stacktrace(), projeto)))
                .orElse(signal);
    }

    private String localizacao(String stacktrace, ProjectEntry projeto) {
        return stacktrace == null || stacktrace.isBlank()
                ? null
                : frameSelector.topFrameDoProjeto(stacktrace, projeto.pacotesRaiz());
    }
}
