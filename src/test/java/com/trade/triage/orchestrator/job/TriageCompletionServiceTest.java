package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.board.model.PullRequestRef;
import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.GateFacts;
import com.trade.triage.gate.PolicyGate;
import com.trade.triage.gate.verification.GateFactsVerifier;
import com.trade.triage.metrics.TriageMetrics;
import com.trade.triage.orchestrator.publish.AnalysisCommentBuilder;
import com.trade.triage.orchestrator.publish.PatchPublisher;
import com.trade.triage.orchestrator.publish.PullRequestBodyBuilder;
import com.trade.triage.orchestrator.result.InvalidResultException;
import com.trade.triage.orchestrator.result.ProposedTest;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.orchestrator.result.TriageResultReader;
import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.DecisionRecordEntity;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.DecisionRecordRepository;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageCompletionServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");
    private static final CardRef CARD = new CardRef("acme/trade", 123);

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master",
            List.of("mvn", "test"), List.of("producao"), "acme/trade", List.of(), Map.of(),
            ProjectLimits.conservador(), true);

    @Mock
    private TriageJobRepository jobRepository;
    @Mock
    private FingerprintRepository fingerprintRepository;
    @Mock
    private DecisionRecordRepository decisionRepository;
    @Mock
    private ProjectRegistry registry;
    @Mock
    private TriageResultReader resultReader;
    @Mock
    private GateFactsVerifier verifier;
    @Mock
    private PolicyGate gate;
    @Mock
    private PatchPublisher patchPublisher;
    @Mock
    private BoardClient board;
    @Mock
    private TriageMetrics metrics;

    private TriageCompletionService service;

    @BeforeEach
    void setUp() {
        service = new TriageCompletionService(jobRepository, fingerprintRepository, decisionRepository,
                registry, resultReader, verifier, gate, patchPublisher, new AnalysisCommentBuilder(),
                new PullRequestBodyBuilder(), board, metrics, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(registry.version()).thenReturn("v1");
    }

    @Test
    void decisaoHumanBarraOJobEEscalaOCard() {
        TriageJobEntity job = preparar();
        FingerprintEntity estado = prepararFingerprint();
        when(resultReader.read(job)).thenReturn(resultado(true));
        when(verifier.verificar(any(), any(), any(), anyInt())).thenReturn(fatos());
        when(gate.decidir(any(), any())).thenReturn(
                new GateDecision(Decision.HUMAN, "clausula 3", "area critica"));

        service.concluir("job-1");

        assertThat(job.getState()).isEqualTo(JobState.BARRADO);
        assertThat(estado.getState()).isEqualTo(FingerprintState.AGUARDANDO_HUMANO);
        verify(board).aplicarLabel(CARD, "aguardando-humano");
        verify(patchPublisher, never()).publicarBranch(any(), any(), any(), any(), any());
    }

    @Test
    void decisaoHumanAindaPostaAAnaliseNoCard() {
        TriageJobEntity job = preparar();
        prepararFingerprint();
        when(resultReader.read(job)).thenReturn(resultado(true));
        when(verifier.verificar(any(), any(), any(), anyInt())).thenReturn(fatos());
        when(gate.decidir(any(), any())).thenReturn(
                new GateDecision(Decision.HUMAN, "clausula 6", "nao existe teste que reproduza o erro"));

        service.concluir("job-1");

        ArgumentCaptor<String> comentario = ArgumentCaptor.forClass(String.class);
        verify(board).comentar(eq(CARD), comentario.capture());
        assertThat(comentario.getValue())
                .contains("Analise do agente")
                .contains("Evidencia contra")
                .contains("clausula 6");
    }

    @Test
    void propostaLiberadaAbrePullRequestEContaTentativaAutomatica() {
        TriageJobEntity job = preparar();
        FingerprintEntity estado = prepararFingerprint();
        when(resultReader.read(job)).thenReturn(resultado(true));
        when(verifier.verificar(any(), any(), any(), anyInt())).thenReturn(fatos());
        when(gate.decidir(any(), any())).thenReturn(
                new GateDecision(Decision.PROPOSE_PATCH, "clausula 9", "severidade critica"));
        when(decisionRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(patchPublisher.publicarBranch(any(), any(), any(), any(), any())).thenReturn("triagem/f1");
        when(board.abrirPullRequest(eq("acme/trade"), any()))
                .thenReturn(new PullRequestRef("acme/trade", 77, "https://github.com/acme/trade/pull/77"));

        service.concluir("job-1");

        assertThat(job.getState()).isEqualTo(JobState.PUBLICADO);
        assertThat(estado.getAutoAttempts()).isEqualTo(1);
        assertThat(estado.getState()).isEqualTo(FingerprintState.EM_CORRECAO);
        verify(board).aplicarLabel(CARD, "decisao/proposta");
    }

    @Test
    void analiseSemPropostaNuncaAbrePullRequest() {
        TriageJobEntity job = preparar();
        prepararFingerprint();
        when(resultReader.read(job)).thenReturn(resultado(false));
        when(verifier.verificar(any(), any(), any(), anyInt())).thenReturn(fatos());
        when(gate.decidir(any(), any())).thenReturn(
                new GateDecision(Decision.PROPOSE_PATCH, "clausula 9", "severidade critica"));
        when(decisionRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        service.concluir("job-1");

        assertThat(job.getState()).isEqualTo(JobState.BARRADO);
        verify(board, never()).abrirPullRequest(anyString(), any());
    }

    @Test
    void resultadoInvalidoMarcaInvalidoEEscalaParaHumano() {
        TriageJobEntity job = preparar();
        FingerprintEntity estado = prepararFingerprint();
        when(resultReader.read(job)).thenThrow(new InvalidResultException("resultado.json fora do schema"));

        service.concluir("job-1");

        assertThat(job.getState()).isEqualTo(JobState.INVALIDO);
        assertThat(estado.getState()).isEqualTo(FingerprintState.AGUARDANDO_HUMANO);
        verify(board).aplicarLabel(CARD, "aguardando-humano");
        verify(gate, never()).decidir(any(), any());
    }

    @Test
    void registraDecisaoComVersaoDoRegistroVigente() {
        TriageJobEntity job = preparar();
        prepararFingerprint();
        when(resultReader.read(job)).thenReturn(resultado(true));
        when(verifier.verificar(any(), any(), any(), anyInt())).thenReturn(fatos());
        when(gate.decidir(any(), any())).thenReturn(
                new GateDecision(Decision.HUMAN, "clausula 4", "fora da allowlist"));
        when(decisionRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        service.concluir("job-1");

        ArgumentCaptor<DecisionRecordEntity> captor = ArgumentCaptor.forClass(DecisionRecordEntity.class);
        verify(decisionRepository).save(captor.capture());
        assertThat(captor.getValue().getRegistroVersao()).isEqualTo("v1");
        assertThat(captor.getValue().getRegraDecisora()).isEqualTo("clausula 4");
        assertThat(captor.getValue().getDecisao()).isEqualTo(Decision.HUMAN);
    }

    @Test
    void jobForaDoEstadoProntoEhIgnorado() {
        TriageJobEntity job = new TriageJobEntity("job-1", "f1", "trade", "acme/trade", CARD.asString(),
                "issues.labeled", AGORA, AGORA.plus(Duration.ofMinutes(30)));
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        service.concluir("job-1");

        verify(resultReader, never()).read(any());
    }

    private TriageJobEntity preparar() {
        TriageJobEntity job = new TriageJobEntity("job-1", "f1", "trade", "acme/trade", CARD.asString(),
                "issues.labeled", AGORA, AGORA.plus(Duration.ofMinutes(30)));
        job.transicionar(JobState.EXECUTANDO, AGORA);
        job.transicionar(JobState.PRONTO, AGORA);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        return job;
    }

    private FingerprintEntity prepararFingerprint() {
        FingerprintEntity estado = new FingerprintEntity("f1", "trade-backend", "producao", "trade",
                "r1", "critical", AGORA);
        estado.vincularCard(CARD.asString());
        when(fingerprintRepository.findById("f1")).thenReturn(Optional.of(estado));
        lenient().when(registry.findByRepository("acme/trade")).thenReturn(Optional.of(projeto));
        return estado;
    }

    private GateFacts fatos() {
        return new GateFacts(List.of("src/main/java/com/trade/parser/Candle.java"), 12, true, true, true,
                false, BlastRadius.BAIXO, "critical", false, 0);
    }

    private TriageResult resultado(boolean comProposta) {
        return new TriageResult("job-1", CARD.asString(), "exitReason nao e preenchido",
                List.of("os outros caminhos preenchem"), List.of("nao ocorre em homologacao"),
                comProposta ? "diff --git a/x b/x\n" : null,
                comProposta ? new ProposedTest("src/test/java/XTest.java", "XTest#y") : null,
                "preenche exitReason no fechamento por breaker",
                "E esperado que exitReason venha nulo quando o breaker fecha a posicao?");
    }
}
