package com.trade.triage.orchestrator.job;

import com.trade.triage.persistence.entity.FingerprintEntity;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.FingerprintRepository;
import com.trade.triage.persistence.repository.TriageJobRepository;
import com.trade.triage.registry.ProjectRegistry;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import com.trade.triage.shared.control.KillSwitch;
import com.trade.triage.shared.control.KillSwitchProperties;
import com.trade.triage.shared.exception.NotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTriageJobServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");
    private static final String CARD = "acme/trade#123";

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master", List.of("mvn", "test"),
            List.of("producao"), "acme/trade", List.of(), Map.of(), ProjectLimits.conservador(), true);

    @Mock
    private TriageJobRepository jobRepository;
    @Mock
    private FingerprintRepository fingerprintRepository;
    @Mock
    private ProjectRegistry registry;

    private DefaultTriageJobService service;

    @BeforeEach
    void setUp() {
        service = criar(new KillSwitch(new KillSwitchProperties("build/nao-existe", false)));
    }

    @Test
    void enfileiraJobPendenteParaCardConhecido() {
        when(fingerprintRepository.findByCardRef(CARD)).thenReturn(Optional.of(fingerprint()));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.of(projeto));
        when(jobRepository.findByCardRefAndStateIn(anyString(), anyList())).thenReturn(Optional.empty());

        JobEnqueueResult resultado = service.enfileirar(CARD, "issues.labeled");

        assertThat(resultado.resultado()).isEqualTo(JobEnqueueOutcome.ENFILEIRADO);
        ArgumentCaptor<TriageJobEntity> captor = ArgumentCaptor.forClass(TriageJobEntity.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(JobState.PENDENTE);
        assertThat(captor.getValue().getPrazo()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
    }

    @Test
    void naoEnfileiraSegundoJobParaOMesmoCard() {
        when(fingerprintRepository.findByCardRef(CARD)).thenReturn(Optional.of(fingerprint()));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.of(projeto));
        when(jobRepository.findByCardRefAndStateIn(anyString(), anyList()))
                .thenReturn(Optional.of(job(JobState.PENDENTE)));

        JobEnqueueResult resultado = service.enfileirar(CARD, "issues.labeled");

        assertThat(resultado.resultado()).isEqualTo(JobEnqueueOutcome.JA_EXISTE_JOB_ATIVO);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void cardQueNaoVeioDaTriagemNaoEnfileira() {
        when(fingerprintRepository.findByCardRef(CARD)).thenReturn(Optional.empty());

        assertThat(service.enfileirar(CARD, "issues.labeled").resultado())
                .isEqualTo(JobEnqueueOutcome.CARD_DESCONHECIDO);
    }

    @Test
    void repositorioForaDoRegistroNaoEnfileira() {
        when(fingerprintRepository.findByCardRef(CARD)).thenReturn(Optional.of(fingerprint()));
        when(registry.findByRepository("acme/trade")).thenReturn(Optional.empty());

        assertThat(service.enfileirar(CARD, "issues.labeled").resultado())
                .isEqualTo(JobEnqueueOutcome.PROJETO_FORA_DO_REGISTRO);
    }

    @Test
    void killSwitchAcionadoNaoEnfileira() {
        DefaultTriageJobService desligado = criar(new KillSwitch(new KillSwitchProperties("build/x", true)));

        assertThat(desligado.enfileirar(CARD, "issues.labeled").resultado())
                .isEqualTo(JobEnqueueOutcome.SUPRIMIDO_POR_KILL_SWITCH);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void sinalDeConclusaoLevaJobDeExecutandoParaPronto() {
        TriageJobEntity job = job(JobState.EXECUTANDO);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        service.marcarPronto("job-1");

        assertThat(job.getState()).isEqualTo(JobState.PRONTO);
    }

    @Test
    void sinalRepetidoEhDescartadoSemErro() {
        TriageJobEntity job = job(JobState.EXECUTANDO);
        job.transicionar(JobState.PRONTO, AGORA);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        service.marcarPronto("job-1");

        assertThat(job.getState()).isEqualTo(JobState.PRONTO);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void sinalDeFalhaMarcaFalhouComMotivo() {
        TriageJobEntity job = job(JobState.EXECUTANDO);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        service.marcarFalha("job-1", "runner caiu");

        assertThat(job.getState()).isEqualTo(JobState.FALHOU);
        assertThat(job.getMotivo()).isEqualTo("runner caiu");
    }

    @Test
    void sinalDeJobDesconhecidoFalha() {
        when(jobRepository.findById("job-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarPronto("job-x")).isInstanceOf(NotFoundException.class);
    }

    private DefaultTriageJobService criar(KillSwitch killSwitch) {
        return new DefaultTriageJobService(jobRepository, fingerprintRepository, registry, killSwitch,
                new OrchestratorProperties(2, Duration.ofMinutes(30), "https://triagem.interno"),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private FingerprintEntity fingerprint() {
        FingerprintEntity entity = new FingerprintEntity("f1", "trade-backend", "producao", "trade", "r1", AGORA);
        entity.vincularCard(CARD);
        return entity;
    }

    private TriageJobEntity job(JobState estado) {
        TriageJobEntity job = new TriageJobEntity("job-1", "f1", "trade", "acme/trade", CARD,
                "issues.labeled", AGORA, AGORA.plus(Duration.ofMinutes(30)));
        if (estado == JobState.EXECUTANDO) {
            job.transicionar(JobState.EXECUTANDO, AGORA);
        }
        return job;
    }
}
