package com.trade.triage.persistence;

import com.trade.triage.persistence.entity.IllegalJobTransitionException;
import com.trade.triage.persistence.entity.JobState;
import com.trade.triage.persistence.entity.TriageJobEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageJobEntityTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T12:00:00Z");

    @Test
    void nasceEmPendente() {
        assertThat(job().getState()).isEqualTo(JobState.PENDENTE);
    }

    @Test
    void percorreCaminhoFelizAtePublicado() {
        TriageJobEntity job = job();

        job.transicionar(JobState.EXECUTANDO, AGORA);
        job.transicionar(JobState.PRONTO, AGORA);
        job.transicionar(JobState.AVALIADO, AGORA);
        job.transicionar(JobState.PUBLICADO, AGORA);

        assertThat(job.getState()).isEqualTo(JobState.PUBLICADO);
        assertThat(job.getState().escalaParaHumano()).isFalse();
    }

    @Test
    void rejeitaTransicaoQuePulaEtapa() {
        TriageJobEntity job = job();

        assertThatThrownBy(() -> job.transicionar(JobState.AVALIADO, AGORA))
                .isInstanceOf(IllegalJobTransitionException.class);
    }

    @Test
    void rejeitaTransicaoSaindoDeEstadoTerminal() {
        TriageJobEntity job = job();
        job.transicionar(JobState.EXECUTANDO, AGORA);
        job.transicionar(JobState.FALHOU, AGORA);

        assertThatThrownBy(() -> job.transicionar(JobState.PRONTO, AGORA))
                .isInstanceOf(IllegalJobTransitionException.class);
    }

    @Test
    void resultadoInvalidoEscalaParaHumano() {
        TriageJobEntity job = job();
        job.transicionar(JobState.EXECUTANDO, AGORA);
        job.transicionar(JobState.PRONTO, AGORA);
        job.transicionar(JobState.INVALIDO, AGORA, "resultado.json fora do schema");

        assertThat(job.getState().escalaParaHumano()).isTrue();
        assertThat(job.getMotivo()).isEqualTo("resultado.json fora do schema");
    }

    @Test
    void jobEmExecucaoAlemDoPrazoEstaVencido() {
        TriageJobEntity job = job();
        job.transicionar(JobState.EXECUTANDO, AGORA);

        assertThat(job.vencido(AGORA.plus(Duration.ofMinutes(31)))).isTrue();
        assertThat(job.vencido(AGORA.plus(Duration.ofMinutes(10)))).isFalse();
    }

    @Test
    void jobTerminalNuncaVence() {
        TriageJobEntity job = job();
        job.transicionar(JobState.EXECUTANDO, AGORA);
        job.transicionar(JobState.FALHOU, AGORA);

        assertThat(job.vencido(AGORA.plus(Duration.ofDays(1)))).isFalse();
    }

    private TriageJobEntity job() {
        return new TriageJobEntity("job-1", "a3f9c2d1", "trade", "acme/trade", "acme/trade#123",
                "issues.labeled", AGORA, AGORA.plus(Duration.ofMinutes(30)));
    }
}
