package com.trade.triage.gateway.scrub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogLineScrubberTest {

    private final LogLineScrubber scrubber = new LogLineScrubber(
            new SecretScrubber(new ScrubProperties(null, null)), new ObjectMapper());

    @Test
    void mantemCampoConhecido() {
        String limpo = scrubber.scrub("{\"service\":\"trade-backend\",\"level\":\"ERROR\"}");

        assertThat(limpo).contains("trade-backend").contains("ERROR");
    }

    @Test
    void redigeCampoDesconhecido() {
        String limpo = scrubber.scrub("{\"service\":\"trade-backend\",\"campo_do_time\":\"dado cru\"}");

        assertThat(limpo).doesNotContain("dado cru").contains(SecretScrubber.REDIGIDO);
    }

    @Test
    void redigeCampoSensivelMesmoQueConhecido() {
        String limpo = scrubber.scrub("{\"authorization\":\"Bearer abcdefghijklmnop\"}");

        assertThat(limpo).doesNotContain("abcdefghijklmnop");
    }

    @Test
    void redigeSegredoDentroDeCampoConhecido() {
        String limpo = scrubber.scrub("{\"message\":\"falhou com token=abc123secreto\"}");

        assertThat(limpo).doesNotContain("abc123secreto").contains("falhou");
    }

    @Test
    void linhaNaoEstruturadaCaiParaScrubDeTexto() {
        String limpo = scrubber.scrub("ERROR posicao fechada chave sk_live_ABCdef123456789");

        assertThat(limpo).doesNotContain("sk_live_ABCdef123456789").contains("ERROR posicao fechada");
    }

    @Test
    void jsonQueNaoEhObjetoCaiParaScrubDeTexto() {
        assertThat(scrubber.scrub("[1, 2, 3]")).isEqualTo("[1, 2, 3]");
    }

    @Test
    void linhaVaziaPassaDireto() {
        assertThat(scrubber.scrub("  ")).isEqualTo("  ");
        assertThat(scrubber.scrub(null)).isNull();
    }
}
