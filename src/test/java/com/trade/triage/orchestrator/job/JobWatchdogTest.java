package com.trade.triage.orchestrator.job;

import com.trade.triage.board.BoardClient;
import com.trade.triage.board.model.CardRef;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.persistence.repository.TriageJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobWatchdogTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T13:00:00Z");

    @Mock
    private TriageJobRepository repository;
    @Mock
    private BoardClient board;

    @Test
    void expiraJobVencidoEEscalaParaHumano() {
        TriageJobEntity job = executando();
        when(repository.findByStateInAndPrazoBefore(anyList(), any())).thenReturn(List.of(job));

        watchdog().expirarJobsVencidos();

        assertThat(job.getState()).isEqualTo(JobState.EXPIRADO);
        assertThat(job.getState().escalaParaHumano()).isTrue();
        verify(board).comentar(eq(new CardRef("acme/trade", 123)), anyString());
        verify(board).aplicarLabel(new CardRef("acme/trade", 123), "aguardando-humano");
    }

    @Test
    void semJobVencidoNaoTocaNoBoard() {
        when(repository.findByStateInAndPrazoBefore(anyList(), any())).thenReturn(List.of());

        watchdog().expirarJobsVencidos();

        verify(board, never()).comentar(any(), anyString());
    }

    private JobWatchdog watchdog() {
        return new JobWatchdog(repository, board, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private TriageJobEntity executando() {
        Instant criacao = AGORA.minus(Duration.ofHours(2));
        TriageJobEntity job = new TriageJobEntity("job-1", "f1", "trade", "acme/trade", "acme/trade#123",
                "issues.labeled", criacao, criacao.plus(Duration.ofMinutes(30)));
        job.transicionar(JobState.EXECUTANDO, criacao);
        return job;
    }
}
