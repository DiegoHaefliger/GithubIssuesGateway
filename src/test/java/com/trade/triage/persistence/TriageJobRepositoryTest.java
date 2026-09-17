package com.trade.triage.persistence;

import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.TriageJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TriageJobRepositoryTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    @Autowired
    private TriageJobRepository repository;

    @Test
    void listaPendentesNaOrdemDeChegada() {
        repository.save(job("job-2", AGORA.plus(Duration.ofSeconds(10))));
        repository.save(job("job-1", AGORA));

        assertThat(repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE))
                .extracting(TriageJobEntity::getJobId)
                .containsExactly("job-1", "job-2");
    }

    @Test
    void encontraJobsVencidosParaOWatchdog() {
        TriageJobEntity executando = job("job-1", AGORA);
        executando.transicionar(JobState.EXECUTANDO, AGORA);
        repository.save(executando);

        List<TriageJobEntity> vencidos = repository.findByStateInAndPrazoBefore(
                List.of(JobState.PENDENTE, JobState.EXECUTANDO), AGORA.plus(Duration.ofMinutes(31)));

        assertThat(vencidos).extracting(TriageJobEntity::getJobId).containsExactly("job-1");
    }

    @Test
    void naoEncontraJobDentroDoPrazo() {
        repository.save(job("job-1", AGORA));

        assertThat(repository.findByStateInAndPrazoBefore(
                List.of(JobState.PENDENTE, JobState.EXECUTANDO), AGORA.plus(Duration.ofMinutes(5))))
                .isEmpty();
    }

    @Test
    void encontraJobAtivoDoCardParaGarantirUmAgentePorCard() {
        repository.save(job("job-1", AGORA));

        assertThat(repository.findByCardRefAndStateIn("acme/trade#123",
                List.of(JobState.PENDENTE, JobState.EXECUTANDO))).isPresent();
    }

    @Test
    void contaJobsDoProjetoNaJanelaParaOTetoPorHora() {
        repository.save(job("job-1", AGORA));
        repository.save(job("job-2", AGORA));

        assertThat(repository.countByProjetoAndCriadoEmAfter("trade", AGORA.minus(Duration.ofHours(1))))
                .isEqualTo(2);
    }

    private TriageJobEntity job(String jobId, Instant criadoEm) {
        return new TriageJobEntity(jobId, "a3f9c2d1", "trade", "acme/trade", "acme/trade#123",
                "issues.labeled", criadoEm, criadoEm.plus(Duration.ofMinutes(30)));
    }
}
