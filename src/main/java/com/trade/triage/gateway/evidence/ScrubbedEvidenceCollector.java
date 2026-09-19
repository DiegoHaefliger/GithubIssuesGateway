package com.trade.triage.gateway.evidence;

import com.trade.triage.gateway.evidence.observability.ObservabilityClient;
import com.trade.triage.gateway.evidence.observability.TraceClient;
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
    private static final Duration JANELA_DEPLOYS = Duration.ofHours(24);

    private final ObservabilityClient observability;
    private final TraceClient traces;
    private final CommitHistory commitHistory;
    private final DeploymentHistory deploymentHistory;
    private final SecretScrubber scrubber;
    private final LogLineScrubber logLineScrubber;
    private final Clock clock;

    public ScrubbedEvidenceCollector(ObservabilityClient observability, TraceClient traces,
                                     CommitHistory commitHistory, DeploymentHistory deploymentHistory,
                                     SecretScrubber scrubber, LogLineScrubber logLineScrubber, Clock clock) {
        this.observability = observability;
        this.traces = traces;
        this.commitHistory = commitHistory;
        this.deploymentHistory = deploymentHistory;
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

        List<String> logsDoTrace = List.of();
        List<String> spansDoTrace = List.of();
        if (signal.traceIdOpcional().isPresent()) {
            String traceId = signal.traceId();
            logsDoTrace = observability.logsPorTrace(traceId, inicio, fim);
            spansDoTrace = traces.spansDoTrace(traceId);
            if (spansDoTrace.isEmpty()) {
                lacunas.add("trace " + traceId + " nao encontrado no Tempo, ou o Tempo nao respondeu");
            }
        } else {
            lacunas.add("sem trace_id no log: evidencia degradada para janela do servico");
        }

        List<String> logsDoServico = observability.logsPorServico(signal.service(), signal.env(), inicio, fim);
        if (logsDoServico.isEmpty()) {
            lacunas.add("Loki nao devolveu log de erro do servico na janela: consulta falhou ou log ainda nao indexado");
        }

        List<String> commits = commitHistory.commitsRecentes(projeto, JANELA_COMMITS);
        if (commits.isEmpty()) {
            lacunas.add("historico de commits indisponivel em " + projeto.repositorio());
        }

        List<String> deploys = deploymentHistory.deploysRecentes(projeto, signal.env(), JANELA_DEPLOYS);
        if (deploys.isEmpty()) {
            lacunas.add("nenhum deploy registrado nas ultimas 24h, ou a API de deployments nao respondeu");
        }
        lacunas.add("estado de flags e config vigente nao coletado: nao ha servico de config no ambiente");

        return new EvidencePackage(
                UUID.randomUUID().toString(),
                fingerprint,
                signal.service(),
                signal.env(),
                momento,
                scrubber.scrubText(signal.stacktrace()),
                limpar(logsDoTrace),
                spansDoTrace.stream().map(scrubber::scrubText).toList(),
                limpar(logsDoServico),
                observability.serieDeErros(signal.service(), signal.env(), momento.minus(JANELA_SERIE), fim),
                observability.metricasDoServico(signal.service(), inicio, fim),
                deploys,
                commits,
                signal.painelUrl(),
                List.copyOf(lacunas));
    }

    private List<String> limpar(List<String> linhas) {
        return linhas.stream().map(logLineScrubber::scrub).toList();
    }
}
