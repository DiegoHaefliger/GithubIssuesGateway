package com.trade.triage.gateway.evidence;

import com.trade.triage.gateway.evidence.observability.ObservabilityClient;
import com.trade.triage.gateway.model.ErrorSignal;
import com.trade.triage.gateway.scrub.LogLineScrubber;
import com.trade.triage.gateway.scrub.ScrubProperties;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrubbedEvidenceCollectorTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master", List.of("mvn", "test"),
            List.of("producao"), "acme/trade", List.of(), Map.of(), ProjectLimits.conservador(), true);

    @Mock
    private ObservabilityClient observability;
    @Mock
    private CommitHistory commitHistory;
    @Mock
    private DeploymentHistory deploymentHistory;

    private ScrubbedEvidenceCollector collector;

    @BeforeEach
    void setUp() {
        SecretScrubber scrubber = new SecretScrubber(new ScrubProperties(null, null));
        collector = new ScrubbedEvidenceCollector(observability, commitHistory, deploymentHistory, scrubber,
                new LogLineScrubber(scrubber, new com.fasterxml.jackson.databind.ObjectMapper()),
                Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(observability.logsPorServico(anyString(), anyString(), any(), any())).thenReturn(List.of());
        lenient().when(observability.serieDeErros(anyString(), anyString(), any(), any())).thenReturn(List.of());
        lenient().when(observability.metricasDoServico(anyString(), any(), any())).thenReturn(Map.of());
        lenient().when(commitHistory.commitsRecentes(any(), any())).thenReturn(List.of("abc123 alguem fix"));
        lenient().when(deploymentHistory.deploysRecentes(any(), anyString(), any()))
                .thenReturn(List.of("52709a0 master em 2026-09-17T09:00:00Z"));
    }

    @Test
    void usaTraceIdQuandoPresente() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of("linha"));

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.logsDoTrace()).containsExactly("linha");
        verify(observability).logsPorTrace("t1", AGORA.minus(Duration.ofMinutes(5)),
                AGORA.plus(Duration.ofMinutes(5)));
    }

    @Test
    void semTraceIdRegistraLacunaEDegradaParaJanelaDoServico() {
        EvidencePackage pacote = collector.collect(sinal(null), projeto, "f1");

        assertThat(pacote.logsDoTrace()).isEmpty();
        assertThat(pacote.lacunas()).anyMatch(lacuna -> lacuna.contains("sem trace_id"));
        verify(observability, never()).logsPorTrace(anyString(), any(), any());
        verify(observability).logsPorServico("trade-backend", "producao",
                AGORA.minus(Duration.ofMinutes(5)), AGORA.plus(Duration.ofMinutes(5)));
    }

    @Test
    void registraLacunaDeTraceDistribuidoSempre() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of());

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.lacunas()).anyMatch(lacuna -> lacuna.contains("Tempo nao esta implantado"));
    }

    @Test
    void redigeSegredoDoStacktraceEDosLogs() {
        when(observability.logsPorTrace(anyString(), any(), any()))
                .thenReturn(List.of("chamada com token=abc123secreto"));

        EvidencePackage pacote = collector.collect(sinalComSegredo(), projeto, "f1");

        assertThat(pacote.stacktrace()).doesNotContain("sk_live_ABCdef123456789");
        assertThat(pacote.logsDoTrace().getFirst()).doesNotContain("abc123secreto");
    }

    @Test
    void linhaDeLogEstruturadaTemCampoDesconhecidoRedigido() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of(
                "{\"service\":\"trade-backend\",\"message\":\"boom\",\"campo_novo\":\"valor sensivel\"}"));

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.logsDoTrace().getFirst())
                .contains("trade-backend")
                .doesNotContain("valor sensivel");
    }

    @Test
    void leCommitsDoDiretorioDoProjeto() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of());

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.commitsSuspeitos()).containsExactly("abc123 alguem fix");
        verify(commitHistory).commitsRecentes(projeto, Duration.ofHours(24));
    }

    @Test
    void trazDeploysDasUltimas24hEOLinkDoPainel() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of());

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.deploysRecentes()).containsExactly("52709a0 master em 2026-09-17T09:00:00Z");
        assertThat(pacote.painelUrl()).isEqualTo("https://grafana/d/painel");
        verify(deploymentHistory).deploysRecentes(projeto, "producao", Duration.ofHours(24));
    }

    @Test
    void semDeployRegistraLacuna() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of());
        when(deploymentHistory.deploysRecentes(any(), anyString(), any())).thenReturn(List.of());

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.lacunas()).anyMatch(lacuna -> lacuna.contains("deploy"));
    }

    @Test
    void registraQueFlagsEConfigNaoSaoColetadas() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of());

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.lacunas()).anyMatch(lacuna -> lacuna.contains("flags e config"));
    }

    @Test
    void semCommitsRegistraLacuna() {
        when(observability.logsPorTrace(anyString(), any(), any())).thenReturn(List.of());
        when(commitHistory.commitsRecentes(any(), any())).thenReturn(List.of());

        EvidencePackage pacote = collector.collect(sinal("t1"), projeto, "f1");

        assertThat(pacote.lacunas()).anyMatch(lacuna -> lacuna.contains("acme/trade"));
    }

    private ErrorSignal sinal(String traceId) {
        return new ErrorSignal("trade-backend", "producao", "r1", "critical", traceId,
                "com.trade.X", "java.lang.NullPointerException", "at com.trade.X.y(X.java:1)",
                "boom", "https://grafana/d/painel", AGORA, null);
    }

    private ErrorSignal sinalComSegredo() {
        return new ErrorSignal("trade-backend", "producao", "r1", "critical", "t1",
                "com.trade.X", "java.lang.NullPointerException",
                "at com.trade.X.y(X.java:1) chave sk_live_ABCdef123456789", "boom", "https://grafana/d/painel", AGORA, null);
    }
}
