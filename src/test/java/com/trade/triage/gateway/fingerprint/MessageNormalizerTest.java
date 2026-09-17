package com.trade.triage.gateway.fingerprint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageNormalizerTest {

    private final MessageNormalizer normalizer = new MessageNormalizer();

    @Test
    void removeUuid() {
        assertThat(normalizer.normalize("ordem 3f2504e0-4f89-41d3-9a0c-0305e82c3301 falhou"))
                .isEqualTo("ordem <uuid> falhou");
    }

    @Test
    void removeTimestamp() {
        assertThat(normalizer.normalize("falha em 2026-09-07T10:00:00Z"))
                .isEqualTo("falha em <timestamp>");
    }

    @Test
    void removePrecoEIdNumerico() {
        assertThat(normalizer.normalize("posicao 4821 fechada a 63412.55"))
                .isEqualTo("posicao <num> fechada a <num>");
    }

    @Test
    void removeCaminhoDeArquivo() {
        assertThat(normalizer.normalize("nao achou /var/lib/trade/config.yml"))
                .isEqualTo("nao achou <path>");
    }

    @Test
    void colapsaEspacosEBaixaCaixa() {
        assertThat(normalizer.normalize("  Falha   GRAVE  ")).isEqualTo("falha grave");
    }

    @Test
    void mensagemNulaViraTextoVazio() {
        assertThat(normalizer.normalize(null)).isEmpty();
    }

    @Test
    void duasOcorrenciasDoMesmoDefeitoNormalizamIgual() {
        String primeira = normalizer.normalize("Posicao 91 de BTCUSDT fechada a 63412.55 em 2026-09-07T10:00:00Z");
        String segunda = normalizer.normalize("Posicao 7742 de BTCUSDT fechada a 61000.10 em 2026-09-09T02:11:00Z");

        assertThat(primeira).isEqualTo(segunda);
    }
}
