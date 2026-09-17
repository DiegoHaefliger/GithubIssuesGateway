package com.trade.triage.metrics;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.repository.DecisionRecordRepository;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TriageMetricsTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private FingerprintRepository fingerprintRepository;
    @Mock
    private TriageJobRepository jobRepository;
    @Mock
    private DecisionRecordRepository decisionRepository;

    @Test
    void contaCardCriadoPorProjeto() {
        TriageMetrics metrics = metrics();

        metrics.contarCardCriado("trade");
        metrics.contarCardCriado("trade");

        assertThat(meterRegistry.get("triagem.cards.criados").tag("projeto", "trade").counter().count())
                .isEqualTo(2);
    }

    @Test
    void contaLogOrfaoPorServico() {
        metrics().contarLogOrfao("servico-novo");

        assertThat(meterRegistry.get("triagem.logs.orfaos").tag("service", "servico-novo").counter().count())
                .isEqualTo(1);
    }

    @Test
    void contaDecisaoPorRegra() {
        metrics().contarDecisao(Decision.HUMAN, "clausula 3");

        assertThat(meterRegistry.get("triagem.decisoes")
                .tag("decisao", "HUMAN").tag("regra", "clausula 3").counter().count()).isEqualTo(1);
    }

    @Test
    void exponhaCardsAguardandoHumanoComoMedidor() {
        lenient().when(fingerprintRepository.countByStateAndLastSeenAfter(
                eq(FingerprintState.AGUARDANDO_HUMANO), any())).thenReturn(3L);
        metrics();

        assertThat(meterRegistry.get("triagem.cards.aguardando_humano").gauge().value()).isEqualTo(3);
    }

    @Test
    void exponhaJobsAtivosComoMedidor() {
        lenient().when(jobRepository.countByStateIn(anyList())).thenReturn(2L);
        metrics();

        assertThat(meterRegistry.get("triagem.jobs.ativos").gauge().value()).isEqualTo(2);
    }

    @Test
    void registraTempoAteAnaliseComoTimer() {
        metrics().registrarTempoAteAnalise("trade", java.time.Duration.ofMinutes(4));

        assertThat(meterRegistry.get("triagem.tempo_ate_analise").tag("projeto", "trade")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void registraMttrPorSeveridade() {
        metrics().registrarMttr("critical", java.time.Duration.ofHours(6));

        assertThat(meterRegistry.get("triagem.mttr").tag("severidade", "critical")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void registraMinutosDeActions() {
        metrics().registrarConsumoDoRunner("trade", java.time.Duration.ofMinutes(7));

        assertThat(meterRegistry.get("triagem.minutos_de_actions").tag("projeto", "trade")
                .timer().totalTime(java.util.concurrent.TimeUnit.MINUTES)).isEqualTo(7);
    }

    @Test
    void contaFalsoPositivoPorRegra() {
        metrics().contarFalsoPositivo("regra-1");

        assertThat(meterRegistry.get("triagem.falsos_positivos").tag("rule_id", "regra-1")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void falsoPositivoSemRegraCaiEmDesconhecido() {
        metrics().contarFalsoPositivo(null);

        assertThat(meterRegistry.get("triagem.falsos_positivos").tag("rule_id", "desconhecido")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void contaDesfechoPorDecisao() {
        metrics().contarDesfecho(com.trade.triage.persistence.entity.Outcome.MERGED, Decision.AUTO_FIX);

        assertThat(meterRegistry.get("triagem.desfechos")
                .tag("desfecho", "MERGED").tag("decisao", "AUTO_FIX").counter().count()).isEqualTo(1);
    }

    private TriageMetrics metrics() {
        return new TriageMetrics(fingerprintRepository, jobRepository, decisionRepository, meterRegistry,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
