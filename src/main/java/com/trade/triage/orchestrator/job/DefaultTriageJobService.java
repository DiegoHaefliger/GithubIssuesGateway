package com.trade.triage.orchestrator.job;

import com.trade.triage.board.model.CardRef;
import com.trade.triage.gateway.service.StormWindow;
import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.shared.control.KillSwitch;
import com.trade.triage.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultTriageJobService implements TriageJobService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTriageJobService.class);
    private static final List<JobState> ATIVOS = List.of(JobState.PENDENTE, JobState.EXECUTANDO);

    private final TriageJobRepository jobRepository;
    private final FingerprintRepository fingerprintRepository;
    private final ProjectRegistry registry;
    private final KillSwitch killSwitch;
    private final OrchestratorProperties properties;
    private final Clock clock;

    public DefaultTriageJobService(TriageJobRepository jobRepository,
                                   FingerprintRepository fingerprintRepository,
                                   ProjectRegistry registry,
                                   KillSwitch killSwitch,
                                   OrchestratorProperties properties,
                                   Clock clock) {
        this.jobRepository = jobRepository;
        this.fingerprintRepository = fingerprintRepository;
        this.registry = registry;
        this.killSwitch = killSwitch;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JobEnqueueResult enfileirar(String cardRef, String eventoOrigem) {
        if (killSwitch.acionado()) {
            return JobEnqueueResult.recusado(JobEnqueueOutcome.SUPRIMIDO_POR_KILL_SWITCH, killSwitch.motivo());
        }
        Optional<FingerprintEntity> estado = fingerprintRepository.findByCardRef(cardRef);
        if (estado.isEmpty()) {
            return JobEnqueueResult.recusado(JobEnqueueOutcome.CARD_DESCONHECIDO,
                    "card " + cardRef + " nao foi criado pela triagem");
        }
        if (StormWindow.ehTempestade(estado.get().getFingerprint())) {
            return JobEnqueueResult.recusado(JobEnqueueOutcome.CARD_DESCONHECIDO,
                    "card de tempestade nao aciona o agente");
        }
        Optional<ProjectEntry> projeto = registry.findByRepository(CardRef.parse(cardRef).repositorio());
        if (projeto.isEmpty()) {
            return JobEnqueueResult.recusado(JobEnqueueOutcome.PROJETO_FORA_DO_REGISTRO,
                    "repositorio do card fora do registro de escopo");
        }
        if (jobRepository.findByCardRefAndStateIn(cardRef, ATIVOS).isPresent()) {
            return JobEnqueueResult.recusado(JobEnqueueOutcome.JA_EXISTE_JOB_ATIVO,
                    "ja existe job ativo para " + cardRef);
        }

        Instant agora = clock.instant();
        TriageJobEntity job = new TriageJobEntity(UUID.randomUUID().toString(),
                estado.get().getFingerprint(), projeto.get().projeto(), projeto.get().repositorio(),
                cardRef, eventoOrigem, agora, agora.plus(properties.prazoDoJob()));
        jobRepository.save(job);

        LOG.info("job enfileirado job={} card={} evento={}", job.getJobId(), cardRef, eventoOrigem);
        return JobEnqueueResult.enfileirado(job.getJobId());
    }

    @Override
    @Transactional
    public void marcarPronto(String jobId) {
        TriageJobEntity job = buscar(jobId);
        if (job.getState() != JobState.EXECUTANDO) {
            LOG.warn("sinal de conclusao descartado job={} estado={}", jobId, job.getState());
            return;
        }
        job.transicionar(JobState.PRONTO, clock.instant());
        jobRepository.save(job);
    }

    @Override
    @Transactional
    public void marcarFalha(String jobId, String motivo) {
        TriageJobEntity job = buscar(jobId);
        if (job.getState() != JobState.EXECUTANDO) {
            LOG.warn("sinal de falha descartado job={} estado={}", jobId, job.getState());
            return;
        }
        job.transicionar(JobState.FALHOU, clock.instant(), motivo);
        jobRepository.save(job);
    }

    private TriageJobEntity buscar(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job desconhecido: " + jobId));
    }
}
