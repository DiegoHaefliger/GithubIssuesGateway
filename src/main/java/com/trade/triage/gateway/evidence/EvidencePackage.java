package com.trade.triage.gateway.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvidencePackage(
        String id,
        String fingerprint,
        String service,
        String env,
        Instant occurredAt,
        String stacktrace,
        List<String> logsDoTrace,
        List<String> spansDoTrace,
        List<String> logsDoServico,
        List<OccurrenceSample> serieDeOcorrencias,
        Map<String, String> metricas,
        List<String> deploysRecentes,
        List<String> commitsSuspeitos,
        String painelUrl,
        List<String> lacunas) {

    public EvidencePackage {
        logsDoTrace = logsDoTrace == null ? List.of() : List.copyOf(logsDoTrace);
        spansDoTrace = spansDoTrace == null ? List.of() : List.copyOf(spansDoTrace);
        logsDoServico = logsDoServico == null ? List.of() : List.copyOf(logsDoServico);
        serieDeOcorrencias = serieDeOcorrencias == null ? List.of() : List.copyOf(serieDeOcorrencias);
        metricas = metricas == null ? Map.of() : Map.copyOf(metricas);
        deploysRecentes = deploysRecentes == null ? List.of() : List.copyOf(deploysRecentes);
        commitsSuspeitos = commitsSuspeitos == null ? List.of() : List.copyOf(commitsSuspeitos);
        lacunas = lacunas == null ? List.of() : List.copyOf(lacunas);
    }
}
