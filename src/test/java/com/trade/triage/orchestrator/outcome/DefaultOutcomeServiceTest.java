package com.trade.triage.orchestrator.outcome;

import com.trade.triage.metrics.TriageMetrics;
import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.DecisionRecordEntity;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.entity.Outcome;
import com.trade.triage.persistence.repository.DecisionRecordRepository;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.registry.model.BlastRadius;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultOutcomeServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");
    private static final String PR = "acme/trade#77";

    @Mock
    private DecisionRecordRepository decisionRepository;
    @Mock
    private FingerprintRepository fingerprintRepository;
    @Mock
    private TriageMetrics metrics;

    private DefaultOutcomeService service;

    @BeforeEach
    void setUp() {
        service = new DefaultOutcomeService(decisionRepository, fingerprintRepository, metrics,
                java.time.Clock.fixed(AGORA.plus(java.time.Duration.ofHours(3)), java.time.ZoneOffset.UTC));
    }

    @Test
    void mergeMarcaDesfechoMergedEResolveFingerprint() {
        DecisionRecordEntity decisao = decisao();
        FingerprintEntity estado = fingerprint();
        when(decisionRepository.findByPrRef(PR)).thenReturn(Optional.of(decisao));
        when(fingerprintRepository.findById("f1")).thenReturn(Optional.of(estado));

        service.registrarPullRequestFechado(PR, true);

        assertThat(decisao.getDesfecho()).isEqualTo(Outcome.MERGED);
        assertThat(estado.getState()).isEqualTo(FingerprintState.RESOLVIDO);
        verify(metrics).contarDesfecho(Outcome.MERGED, Decision.PROPOSE_PATCH);
    }

    @Test
    void prFechadoSemMergeMarcaRejeitadoENaoResolveFingerprint() {
        DecisionRecordEntity decisao = decisao();
        when(decisionRepository.findByPrRef(PR)).thenReturn(Optional.of(decisao));

        service.registrarPullRequestFechado(PR, false);

        assertThat(decisao.getDesfecho()).isEqualTo(Outcome.REJEITADO);
        verify(fingerprintRepository, never()).save(any());
    }

    @Test
    void desfechoJaRegistradoNaoEhSobrescrito() {
        DecisionRecordEntity decisao = decisao();
        decisao.registrarDesfecho(Outcome.MERGED);
        when(decisionRepository.findByPrRef(PR)).thenReturn(Optional.of(decisao));

        service.registrarPullRequestFechado(PR, false);

        assertThat(decisao.getDesfecho()).isEqualTo(Outcome.MERGED);
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void prQueNaoVeioDaTriagemEhIgnorado() {
        when(decisionRepository.findByPrRef("acme/trade#99")).thenReturn(Optional.empty());

        service.registrarPullRequestFechado("acme/trade#99", true);

        verify(decisionRepository, never()).save(any());
    }

    @Test
    void cardFechadoResolveFingerprint() {
        FingerprintEntity estado = fingerprint();
        when(fingerprintRepository.findByCardRef("acme/trade#123")).thenReturn(Optional.of(estado));
        when(decisionRepository.findByFingerprint("f1")).thenReturn(List.of(decisao()));

        service.registrarCardFechado("acme/trade#123");

        assertThat(estado.getState()).isEqualTo(FingerprintState.RESOLVIDO);
        verify(metrics).registrarMttr("critical", java.time.Duration.ofHours(3));
    }

    @Test
    void cardFechadoSemNenhumaDecisaoContaFalsoPositivo() {
        FingerprintEntity estado = fingerprint();
        when(fingerprintRepository.findByCardRef("acme/trade#123")).thenReturn(Optional.of(estado));
        when(decisionRepository.findByFingerprint("f1")).thenReturn(List.of());

        service.registrarCardFechado("acme/trade#123");

        verify(metrics).contarFalsoPositivo("r1");
    }

    @Test
    void cardFechadoComDecisaoNaoContaFalsoPositivo() {
        FingerprintEntity estado = fingerprint();
        when(fingerprintRepository.findByCardRef("acme/trade#123")).thenReturn(Optional.of(estado));
        when(decisionRepository.findByFingerprint("f1")).thenReturn(List.of(decisao()));

        service.registrarCardFechado("acme/trade#123");

        verify(metrics, never()).contarFalsoPositivo(any());
    }

    @Test
    void revertDeCommitDaTriagemMarcaRevertido() {
        DecisionRecordEntity decisao = decisao();
        decisao.registrarDesfecho(Outcome.MERGED);
        when(decisionRepository.findByFingerprintAndDesfecho("f1", Outcome.MERGED))
                .thenReturn(List.of(decisao));
        when(fingerprintRepository.findById("f1")).thenReturn(Optional.of(fingerprint()));

        service.registrarReversoes(List.of("""
                Revert "fix: preenche exitReason"

                This reverts commit 52709a0.

                Card: acme/trade#123
                Fingerprint: f1
                Decisao-Gate: PROPOSE_PATCH (clausula 9)
                """));

        assertThat(decisao.getDesfecho()).isEqualTo(Outcome.REVERTIDO);
        verify(metrics).contarDesfecho(Outcome.REVERTIDO, Decision.PROPOSE_PATCH);
    }

    @Test
    void revertDevolveOFingerprintParaHumano() {
        DecisionRecordEntity decisao = decisao();
        decisao.registrarDesfecho(Outcome.MERGED);
        FingerprintEntity estado = fingerprint();
        estado.mudarEstado(FingerprintState.RESOLVIDO);
        when(decisionRepository.findByFingerprintAndDesfecho("f1", Outcome.MERGED))
                .thenReturn(List.of(decisao));
        when(fingerprintRepository.findById("f1")).thenReturn(Optional.of(estado));

        service.registrarReversoes(List.of("Revert \"fix: x\"\n\nFingerprint: f1\n"));

        assertThat(estado.getState()).isEqualTo(FingerprintState.AGUARDANDO_HUMANO);
    }

    @Test
    void commitComumNaoEhTratadoComoReversao() {
        service.registrarReversoes(List.of("feat: adiciona coluna\n\nFingerprint: f1\n"));

        verify(decisionRepository, never()).findByFingerprintAndDesfecho(any(), any());
    }

    @Test
    void revertSemTrailerDeFingerprintNaoMudaNada() {
        service.registrarReversoes(List.of("Revert \"fix: algo feito a mao\""));

        verify(decisionRepository, never()).findByFingerprintAndDesfecho(any(), any());
    }

    private DecisionRecordEntity decisao() {
        return new DecisionRecordEntity("job-1", "acme/trade#123", "f1", Decision.PROPOSE_PATCH,
                "clausula 9", "v1", 1, 12, true, true, BlastRadius.BAIXO, AGORA);
    }

    private FingerprintEntity fingerprint() {
        FingerprintEntity estado = new FingerprintEntity("f1", "trade-backend", "producao", "trade",
                "r1", "critical", AGORA);
        estado.vincularCard("acme/trade#123");
        return estado;
    }
}
