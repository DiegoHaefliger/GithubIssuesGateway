package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.orchestrator.runner.RunnerCanceller;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.TriageJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class JobWatchdog {

    private static final Logger LOG = LoggerFactory.getLogger(JobWatchdog.class);
    private static final List<JobState> ATIVOS = List.of(JobState.PENDENTE, JobState.EXECUTANDO);
    private static final String LABEL_AGUARDANDO_HUMANO = "aguardando-humano";

    private final TriageJobRepository repository;
    private final RunnerCanceller canceller;
    private final BoardClient board;
    private final Clock clock;

    public JobWatchdog(TriageJobRepository repository, RunnerCanceller canceller,
                       BoardClient board, Clock clock) {
        this.repository = repository;
        this.canceller = canceller;
        this.board = board;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${triage.orquestrador.intervalo-do-watchdog:60s}")
    @Transactional
    public void expirarJobsVencidos() {
        List<TriageJobEntity> vencidos = repository.findByStateInAndPrazoBefore(ATIVOS, clock.instant());
        for (TriageJobEntity job : vencidos) {
            boolean cancelado = canceller.cancelar(job);
            job.transicionar(JobState.EXPIRADO, clock.instant(), motivo(cancelado));
            repository.save(job);
            escalar(job);
            LOG.warn("job expirado pelo watchdog job={} card={}", job.getJobId(), job.getCardRef());
        }
    }

    private String motivo(boolean cancelado) {
        return cancelado
                ? "prazo do job estourou; execucao do runner cancelada"
                : "prazo do job estourou; execucao do runner nao pode ser cancelada";
    }

    private void escalar(TriageJobEntity job) {
        CardRef card = CardRef.parse(job.getCardRef());
        board.comentar(card, """
                A triagem automatica nao concluiu dentro do prazo e foi encerrada pelo watchdog.

                - Job: `%s`
                - Motivo: %s

                O card fica para analise humana.
                """.formatted(job.getJobId(), job.getMotivo()));
        board.aplicarLabel(card, LABEL_AGUARDANDO_HUMANO);
    }
}
