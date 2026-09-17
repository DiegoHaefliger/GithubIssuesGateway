package com.trade.triage.gateway.evidence;

import com.trade.triage.gateway.evidence.observability.ObservabilityClient;
import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.gateway.scrub.LogLineScrubber;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScrubbedEvidenceCollector implements EvidenceCollector {

    private static final Duration JANELA_TRACE = Duration.ofMinutes(5);
    private static final Duration JANELA_SERIE = Duration.ofHours(24);
    private static final Duration JANELA_COMMITS = Duration.ofHours(24);

    private final ObservabilityClient observability;
    private final CommitHistory commitHistory;
    private final SecretScrubber scrubber;
    private final LogLineScrubber logLineScrubber;
    private final Clock clock;

    public ScrubbedEvidenceCollector(ObservabilityClient observability, CommitHistory commitHistory,
                                     SecretScrubber scrubber, LogLineScrubber logLineScrubber, Clock clock) {
        this.observability = observability;
        this.commitHistory = commitHistory;
        this.scrubber = scrubber;
        this.logLineScrubber = logLineScrubber;
        this.clock = clock;
    }

    @Override
    public EvidencePackage collect(ErrorSignal signal, ProjectEntry projeto, String fingerprint) {
        Instant momento = signal.occurredAt() == null ? clock.instant() : signal.occurredAt();
        Instant inicio = momento.minus(JANELA_TRACE);
        Instant fim = momento.plus(JANELA_TRACE);
        List<String> lacunas = new ArrayList<>();
        lacunas.add("trace distribuido indisponivel: Tempo nao esta implantado");

        List<String> logsDoTrace = signal.traceIdOpcional()
                .map(traceId -> observability.logsPorTrace(traceId, inicio, fim))
                .orElseGet(() -> {
                    lacunas.add("sem trace_id no log: evidencia degradada para janela do servico");
                    return List.of();
                });

        List<String> commits = commitHistory.commitsRecentes(projeto, JANELA_COMMITS);
        if (commits.isEmpty()) {
            lacunas.add("historico de commits indisponivel em " + projeto.diretorio());
        }

        return new EvidencePackage(
                UUID.randomUUID().toString(),
                fingerprint,
                signal.service(),
                signal.env(),
                momento,
                scrubber.scrubText(signal.stacktrace()),
                limpar(logsDoTrace),
                limpar(observability.logsPorServico(signal.service(), signal.env(), inicio, fim)),
                observability.serieDeErros(signal.service(), signal.env(), momento.minus(JANELA_SERIE), fim),
                observability.metricasDoServico(signal.service(), inicio, fim),
                List.of(),
                commits,
                null,
                List.copyOf(lacunas));
    }

    private List<String> limpar(List<String> linhas) {
        return linhas.stream().map(logLineScrubber::scrub).toList();
    }
}
