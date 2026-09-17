package com.trade.triage.orchestrator.job;

import com.trade.triage.orchestrator.runner.RunnerDispatcher;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import com.trade.triage.shared.control.KillSwitch;
import com.trade.triage.shared.control.KillSwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master", List.of("mvn", "test"),
            List.of("producao"), "acme/trade", List.of(), Map.of(),
            new ProjectLimits(50, 3, 10, 2), true);

    @Mock
    private TriageJobRepository repository;
    @Mock
    private ProjectRegistry registry;
    @Mock
    private RunnerDispatcher dispatcher;

    private JobWorker worker;

    @BeforeEach
    void setUp() {
        worker = criar(new KillSwitch(new KillSwitchProperties("build/nao-existe", false)));
    }

    @Test
    void disparaRunnerDoJobPendente() {
        TriageJobEntity job = job("job-1");
        when(repository.countByStateIn(anyList())).thenReturn(0L);
        when(repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE)).thenReturn(List.of(job));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.of(projeto));
        when(repository.countByProjetoAndCriadoEmAfter(anyString(), any())).thenReturn(1L);
        when(dispatcher.dispatch(job, projeto)).thenReturn("acme/trade:triage-runner.yml");

        worker.processarFila();

        assertThat(job.getState()).isEqualTo(JobState.EXECUTANDO);
        assertThat(job.getRunnerRef()).isEqualTo("acme/trade:triage-runner.yml");
    }

    @Test
    void respeitaTetoDeJobsSimultaneos() {
        when(repository.countByStateIn(anyList())).thenReturn(2L);
        when(repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE)).thenReturn(List.of(job("job-1")));

        worker.processarFila();

        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void naoDisparaQuandoTetoDeJobsPorHoraEstourou() {
        TriageJobEntity job = job("job-1");
        when(repository.countByStateIn(anyList())).thenReturn(0L);
        when(repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE)).thenReturn(List.of(job));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.of(projeto));
        when(repository.countByProjetoAndCriadoEmAfter(anyString(), any())).thenReturn(3L);

        worker.processarFila();

        verify(dispatcher, never()).dispatch(any(), any());
        assertThat(job.getState()).isEqualTo(JobState.PENDENTE);
    }

    @Test
    void jobDeRepositorioQueSaiuDoRegistroExpira() {
        TriageJobEntity job = job("job-1");
        when(repository.countByStateIn(anyList())).thenReturn(0L);
        when(repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE)).thenReturn(List.of(job));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.empty());

        worker.processarFila();

        assertThat(job.getState()).isEqualTo(JobState.EXPIRADO);
        assertThat(job.getMotivo()).contains("registro de escopo");
    }

    @Test
    void falhaAoDispararExpiraOJob() {
        TriageJobEntity job = job("job-1");
        when(repository.countByStateIn(anyList())).thenReturn(0L);
        when(repository.findByStateOrderByCriadoEmAsc(JobState.PENDENTE)).thenReturn(List.of(job));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.of(projeto));
        when(repository.countByProjetoAndCriadoEmAfter(anyString(), any())).thenReturn(0L);
        when(dispatcher.dispatch(job, projeto)).thenThrow(new IllegalStateException("sem rede"));

        worker.processarFila();

        assertThat(job.getState()).isEqualTo(JobState.EXPIRADO);
        assertThat(job.getMotivo()).isEqualTo("falha ao disparar runner");
    }

    @Test
    void killSwitchAcionadoNaoProcessaFila() {
        JobWorker desligado = criar(new KillSwitch(new KillSwitchProperties("build/x", true)));

        desligado.processarFila();

        verify(repository, never()).findByStateOrderByCriadoEmAsc(any());
    }

    private JobWorker criar(KillSwitch killSwitch) {
        lenient().when(repository.countByStateIn(anyList())).thenReturn(0L);
        return new JobWorker(repository, registry, dispatcher, killSwitch,
                new OrchestratorProperties(2, Duration.ofMinutes(30), "https://triagem.interno"),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private TriageJobEntity job(String jobId) {
        return new TriageJobEntity(jobId, "f1", "trade", "acme/trade", "acme/trade#123",
                "issues.labeled", AGORA, AGORA.plus(Duration.ofMinutes(30)));
    }
}
