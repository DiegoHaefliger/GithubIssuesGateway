package com.trade.triage.gateway.scrub;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretScrubberTest {

    private final SecretScrubber scrubber = new SecretScrubber(new ScrubProperties(null, null));

    @Test
    void redigeTokenBearer() {
        assertThat(scrubber.scrubText("header Authorization: Bearer abcdefghijklmnop12345"))
                .doesNotContain("abcdefghijklmnop12345")
                .contains(SecretScrubber.REDIGIDO);
    }

    @Test
    void redigeChaveDeExchange() {
        assertThat(scrubber.scrubText("usando sk_live_ABCdef123456789"))
                .isEqualTo("usando " + SecretScrubber.REDIGIDO);
    }

    @Test
    void redigeParChaveValorDeSenha() {
        assertThat(scrubber.scrubText("login falhou password=hunter2 usuario=x"))
                .doesNotContain("hunter2");
    }

    @Test
    void redigeEmailECartao() {
        assertThat(scrubber.scrubText("conta de fulano@exemplo.com cartao 4111111111111111"))
                .doesNotContain("fulano@exemplo.com")
                .doesNotContain("4111111111111111");
    }

    @Test
    void redigeChavePrivada() {
        String texto = "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\n-----END RSA PRIVATE KEY-----";

        assertThat(scrubber.scrubText(texto)).isEqualTo(SecretScrubber.REDIGIDO);
    }

    @Test
    void mantemTextoSemSegredo() {
        assertThat(scrubber.scrubText("NullPointerException ao fechar posicao"))
                .isEqualTo("NullPointerException ao fechar posicao");
    }

    @Test
    void campoDesconhecidoSaiRedigido() {
        Map<String, String> limpos = scrubber.scrubFields(Map.of("campo_novo_do_time", "valor qualquer"));

        assertThat(limpos).containsEntry("campo_novo_do_time", SecretScrubber.REDIGIDO);
    }

    @Test
    void campoConhecidoSaiLimpoMasComSegredoRedigido() {
        Map<String, String> limpos = scrubber.scrubFields(
                Map.of("message", "falhou com token=abc123secreto", "service", "trade-backend"));

        assertThat(limpos.get("message")).doesNotContain("abc123secreto");
        assertThat(limpos).containsEntry("service", "trade-backend");
    }

    @Test
    void campoSensivelSaiRedigidoMesmoQueConhecido() {
        assertThat(scrubber.scrubFields(Map.of("authorization", "qualquer")))
                .containsEntry("authorization", SecretScrubber.REDIGIDO);
    }
}
