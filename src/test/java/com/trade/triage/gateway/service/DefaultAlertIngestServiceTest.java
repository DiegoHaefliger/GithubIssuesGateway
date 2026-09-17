package com.trade.triage.gateway.service;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardContent;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.gateway.evidence.EvidenceCollector;
import com.trade.triage.gateway.evidence.EvidencePackage;
import com.trade.triage.gateway.evidence.EvidenceStore;
import com.trade.triage.gateway.fingerprint.FingerprintCalculator;
import com.trade.triage.gateway.fingerprint.MessageNormalizer;
import com.trade.triage.gateway.fingerprint.StackFrameSelector;
import com.trade.triage.gateway.scrub.ScrubProperties;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.gateway.web.GrafanaAlert;
import com.trade.triage.gateway.web.GrafanaWebhookRequest;
import com.trade.triage.metrics.TriageMetrics;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import com.trade.triage.shared.control.IncidentWindow;
import com.trade.triage.shared.control.IncidentWindowProperties;
import com.trade.triage.shared.control.KillSwitch;
import com.trade.triage.shared.control.KillSwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAlertIngestServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");
    private static final String STACK = """
            java.lang.NullPointerException: exitReason is null
                at com.trade.execution.ExitReasonResolver.resolve(ExitReasonResolver.java:88)
            """;

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade",
            "master", List.of("mvn", "test"), List.of("producao"), "acme/trade", List.of(), Map.of(),
            new ProjectLimits(50, 3, 2, 10), true);

    @Mock
    private ProjectRegistry registry;
    @Mock
    private EvidenceCollector evidenceCollector;
    @Mock
    private EvidenceStore evidenceStore;
    @Mock
    private FingerprintRepository repository;
    @Mock
    private BoardClient board;
    @Mock
    private CardRateLimiter rateLimiter;
    @Mock
    private TriageMetrics metrics;

    private DefaultAlertIngestService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        SecretScrubber scrubber = new SecretScrubber(new ScrubProperties(null, null));
        service = new DefaultAlertIngestService(
                registry,
                new FingerprintCalculator(new MessageNormalizer(), new StackFrameSelector()),
                new ErrorSignalExtractor(clock),
                evidenceCollector,
                evidenceStore,
                new CardContentBuilder(scrubber),
                rateLimiter,
                repository,
                board,
                new KillSwitch(new KillSwitchProperties("build/nao-existe", false)),
                new IncidentWindow(new IncidentWindowProperties("build/nao-existe")),
                metrics,
                clock);
    }

    @Test
    void criaCardParaFingerprintNovo() {
        registraProjeto();
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(rateLimiter.estourouTeto(projeto)).thenReturn(false);
        when(evidenceCollector.collect(any(), any(), anyString())).thenReturn(evidencia());
        when(evidenceStore.store(any())).thenReturn("file:///var/evidencia/p1.json");
        when(board.criarCard(anyString(), any())).thenReturn(new CardRef("acme/trade", 123));

        List<IngestResult> resultados = service.ingest(webhook(alerta("Falha ao fechar posicao 42")));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado, IngestResult::cardRef)
                .containsExactly(IngestOutcome.CARD_CRIADO, "acme/trade#123");
        verify(repository).save(any(FingerprintEntity.class));
    }

    @Test
    void cardNasceComLabelAutoTriage() {
        registraProjeto();
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(evidenceCollector.collect(any(), any(), anyString())).thenReturn(evidencia());
        when(evidenceStore.store(any())).thenReturn("file:///p1.json");
        when(board.criarCard(anyString(), any())).thenReturn(new CardRef("acme/trade", 123));

        service.ingest(webhook(alerta("Falha ao fechar posicao 42")));

        ArgumentCaptor<CardContent> captor = ArgumentCaptor.forClass(CardContent.class);
        verify(board).criarCard(anyString(), captor.capture());
        assertThat(captor.getValue().labels()).contains("auto-triage", "severity/critical", "service/trade-backend");
    }

    @Test
    void recorrenciaComentaNoCardSemCriarOutro() {
        registraProjeto();
        FingerprintEntity existente = existente(FingerprintState.TRIADO);
        when(repository.findById(anyString())).thenReturn(Optional.of(existente));

        List<IngestResult> resultados = service.ingest(webhook(alerta("Falha ao fechar posicao 99")));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.DEDUPLICADO);
        assertThat(existente.getOccurrenceCount()).isEqualTo(2);
        verify(board).comentar(any(), anyString());
        verify(board, never()).criarCard(anyString(), any());
    }

    @Test
    void fingerprintResolvidoQueVoltaReabreCardComoRegressao() {
        registraProjeto();
        FingerprintEntity existente = existente(FingerprintState.RESOLVIDO);
        when(repository.findById(anyString())).thenReturn(Optional.of(existente));

        List<IngestResult> resultados = service.ingest(webhook(alerta("Falha ao fechar posicao 99")));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.REGRESSAO_REABERTA);
        verify(board).reabrir(new CardRef("acme/trade", 123));
        verify(board).aplicarLabel(new CardRef("acme/trade", 123), "regressao");
    }

    @Test
    void servicoForaDoRegistroViraLogOrfaoSemCard() {
        when(registry.findByServiceAndEnv("trade-backend", "producao")).thenReturn(Optional.empty());
        when(registry.version()).thenReturn("v1");

        List<IngestResult> resultados = service.ingest(webhook(alerta("qualquer")));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.LOG_ORFAO);
        verify(board, never()).criarCard(anyString(), any());
    }

    @Test
    void tetoDeCardsPorHoraViraStormSemCard() {
        registraProjeto();
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(rateLimiter.estourouTeto(projeto)).thenReturn(true);

        List<IngestResult> resultados = service.ingest(webhook(alerta("Falha ao fechar posicao 42")));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.STORM);
        verify(board, never()).criarCard(anyString(), any());
    }

    @Test
    void killSwitchAcionadoSuprimeTudo() {
        DefaultAlertIngestService desligado = new DefaultAlertIngestService(
                registry, new FingerprintCalculator(new MessageNormalizer(), new StackFrameSelector()),
                new ErrorSignalExtractor(Clock.fixed(AGORA, ZoneOffset.UTC)), evidenceCollector, evidenceStore,
                new CardContentBuilder(new SecretScrubber(new ScrubProperties(null, null))), rateLimiter,
                repository, board, new KillSwitch(new KillSwitchProperties("build/nao-existe", true)),
                new IncidentWindow(new IncidentWindowProperties("build/nao-existe")),
                metrics, Clock.fixed(AGORA, ZoneOffset.UTC));

        List<IngestResult> resultados = desligado.ingest(webhook(alerta("qualquer")));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.SUPRIMIDO_POR_KILL_SWITCH);
        verify(board, never()).criarCard(anyString(), any());
    }

    @Test
    void alertaResolvidoEhIgnorado() {
        GrafanaAlert resolvido = new GrafanaAlert("resolved",
                Map.of("service", "trade-backend", "env", "producao"), Map.of(), AGORA, null);

        List<IngestResult> resultados = service.ingest(webhook(resolvido));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.IGNORADO);
    }

    @Test
    void alertaSemServiceViraLogOrfao() {
        GrafanaAlert semService = new GrafanaAlert("firing", Map.of("env", "producao"), Map.of(), AGORA, null);

        List<IngestResult> resultados = service.ingest(webhook(semService));

        assertThat(resultados).singleElement()
                .extracting(IngestResult::resultado)
                .isEqualTo(IngestOutcome.LOG_ORFAO);
    }

    private void registraProjeto() {
        when(registry.findByServiceAndEnv("trade-backend", "producao")).thenReturn(Optional.of(projeto));
    }

    private FingerprintEntity existente(FingerprintState estado) {
        FingerprintEntity entity = new FingerprintEntity("f1", "trade-backend", "producao", "trade", "r1", "critical", AGORA);
        entity.vincularCard("acme/trade#123");
        entity.mudarEstado(estado);
        return entity;
    }

    private EvidencePackage evidencia() {
        return new EvidencePackage("p1", "f1", "trade-backend", "producao", AGORA, STACK,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), null, List.of());
    }

    private GrafanaWebhookRequest webhook(GrafanaAlert alerta) {
        return new GrafanaWebhookRequest("firing", List.of(alerta));
    }

    private GrafanaAlert alerta(String mensagem) {
        return new GrafanaAlert("firing",
                Map.of("service", "trade-backend", "env", "producao",
                        "severity", "critical", "rule_id", "regra-1"),
                Map.of("exception_class", "java.lang.NullPointerException",
                        "stacktrace", STACK, "message", mensagem, "trace_id", "t1"),
                AGORA, null);
    }
}
