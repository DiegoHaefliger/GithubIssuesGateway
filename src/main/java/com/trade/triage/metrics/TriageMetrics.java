package com.trade.triage.metrics;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.FingerprintState;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.repository.DecisionRecordRepository;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Component
public class TriageMetrics {

    private static final Duration JANELA = Duration.ofDays(7);

    private final FingerprintRepository fingerprintRepository;
    private final TriageJobRepository jobRepository;
    private final DecisionRecordRepository decisionRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public TriageMetrics(FingerprintRepository fingerprintRepository,
                         TriageJobRepository jobRepository,
                         DecisionRecordRepository decisionRepository,
                         MeterRegistry meterRegistry,
                         Clock clock) {
        this.fingerprintRepository = fingerprintRepository;
        this.jobRepository = jobRepository;
        this.decisionRepository = decisionRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        registrarMedidores();
    }

    public void contarCardCriado(String projeto) {
        Counter.builder("triagem.cards.criados").tag("projeto", projeto).register(meterRegistry).increment();
    }

    public void contarLogOrfao(String service) {
        Counter.builder("triagem.logs.orfaos").tag("service", service).register(meterRegistry).increment();
    }

    public void contarDecisao(Decision decisao, String regra) {
        Counter.builder("triagem.decisoes")
                .tag("decisao", decisao.name())
                .tag("regra", regra)
                .register(meterRegistry)
                .increment();
    }

    private void registrarMedidores() {
        meterRegistry.gauge("triagem.cards.aguardando_humano", this,
                metricas -> metricas.fingerprintRepository.countByStateAndLastSeenAfter(
                        FingerprintState.AGUARDANDO_HUMANO, metricas.clock.instant().minus(JANELA)));
        meterRegistry.gauge("triagem.jobs.ativos", this,
                metricas -> metricas.jobRepository.countByStateIn(
                        List.of(JobState.PENDENTE, JobState.EXECUTANDO)));
        meterRegistry.gauge("triagem.decisoes.auto_fix", this,
                metricas -> metricas.decisionRepository.countByDecisao(Decision.AUTO_FIX));
        meterRegistry.gauge("triagem.decisoes.proposta", this,
                metricas -> metricas.decisionRepository.countByDecisao(Decision.PROPOSE_PATCH));
        meterRegistry.gauge("triagem.decisoes.humano", this,
                metricas -> metricas.decisionRepository.countByDecisao(Decision.HUMAN));
    }
}
