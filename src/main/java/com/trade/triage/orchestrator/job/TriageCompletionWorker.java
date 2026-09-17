package com.trade.triage.orchestrator.job;

import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.TriageJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TriageCompletionWorker {

    private static final Logger LOG = LoggerFactory.getLogger(TriageCompletionWorker.class);

    private final TriageJobRepository repository;
    private final TriageCompletionService completionService;

    public TriageCompletionWorker(TriageJobRepository repository, TriageCompletionService completionService) {
        this.repository = repository;
        this.completionService = completionService;
    }

    @Scheduled(fixedDelayString = "${triage.orquestrador.intervalo-da-avaliacao:20s}")
    public void avaliarResultadosProntos() {
        List<TriageJobEntity> prontos = repository.findByStateOrderByCriadoEmAsc(JobState.PRONTO);
        for (TriageJobEntity job : prontos) {
            try {
                completionService.concluir(job.getJobId());
            } catch (RuntimeException exception) {
                LOG.error("falha ao avaliar resultado job={} motivo={}", job.getJobId(), exception.getMessage());
            }
        }
    }
}
