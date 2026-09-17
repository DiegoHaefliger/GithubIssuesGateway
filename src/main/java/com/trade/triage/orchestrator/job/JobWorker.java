package com.trade.triage.orchestrator.job;

import com.trade.triage.orchestrator.runner.RunnerDispatcher;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.shared.control.KillSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class JobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(JobWorker.class);
    private static final Duration JANELA_DE_TETO = Duration.ofHours(1);

    private final TriageJobRepository repository;
    private final ProjectRegistry registry;
    private final RunnerDispatcher dispatcher;
    private final KillSwitch killSwitch;
    private final OrchestratorProperties properties;
    private final Clock clock;

    public JobWorker(TriageJobRepository repository,
                     ProjectRegistry registry,
                     RunnerDispatcher dispatcher,
                     KillSwitch killSwitch,
                     OrchestratorProperties properties,
                     Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.killSwitch = killSwitch;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${triage.orquestrador.intervalo-do-worker:15s}")
    @Transactional
    public void processarFila() {
        if (killSwitch.acionado()) {
            return;
        }
        long emExecucao = repository.countByStateIn(List.of(JobState.EXECUTANDO));
        for (TriageJobEntity job : repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE)) {
            if (emExecucao >= properties.maximoDeJobsSimultaneos()) {
                return;
            }
            if (disparar(job)) {
                emExecucao++;
            }
        }
    }

    private boolean disparar(TriageJobEntity job) {
        Optional<ProjectEntry> projeto = registry.findByRepository(job.getRepositorio());
        if (projeto.isEmpty()) {
            job.transicionar(JobState.EXPIRADO, clock.instant(), "repositorio saiu do registro de escopo");
            repository.save(job);
            return false;
        }
        if (estourouTetoDeJobs(job, projeto.get())) {
            LOG.warn("teto de jobs por hora atingido projeto={} job={}", job.getProjeto(), job.getJobId());
            return false;
        }
        try {
            String runnerRef = dispatcher.dispatch(job, projeto.get());
            job.registrarRunner(runnerRef);
            job.transicionar(JobState.EXECUTANDO, clock.instant());
            repository.save(job);
            return true;
        } catch (RuntimeException exception) {
            LOG.error("falha ao disparar runner job={} motivo={}", job.getJobId(), exception.getMessage());
            job.transicionar(JobState.EXPIRADO, clock.instant(), "falha ao disparar runner");
            repository.save(job);
            return false;
        }
    }

    private boolean estourouTetoDeJobs(TriageJobEntity job, ProjectEntry projeto) {
        long jobsNaJanela = repository.countByProjetoAndCriadoEmAfter(
                job.getProjeto(), clock.instant().minus(JANELA_DE_TETO));
        return jobsNaJanela > projeto.limites().jobsPorHora();
    }
}
