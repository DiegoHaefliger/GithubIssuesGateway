package com.trade.triage.gate.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandRunnerTest {

    private final CommandRunner runner = new CommandRunner();

    @Test
    void naoTravaQuandoSaidaPassaDoLimiteDeLinhasGuardadas() {
        // Antes do fix, Stream.limit(200) parava de drenar o pipe e o processo
        // travava escrevendo assim que enchia o buffer do SO — este teste
        // reproduz exatamente essa condicao: mais linhas de saida do que o
        // CommandRunner guarda. assertTimeoutPreemptively falha o teste (em vez
        // de travar para sempre) se a regressao voltar.
        String comando = "for i in $(seq 1 500); do echo linha-$i; done; echo fim-real";

        CommandResult resultado = assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                runner.run(List.of("bash", "-c", comando), Path.of("."), Duration.ofSeconds(10)));

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.saida()).contains("fim-real");
    }

    @Test
    void guardaAsUltimasLinhasNaoAsPrimeiras() {
        CommandResult resultado = assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                runner.run(List.of("bash", "-c", "for i in $(seq 1 250); do echo linha-$i; done"),
                        Path.of("."), Duration.ofSeconds(10)));

        assertThat(resultado.saida()).contains("linha-250");
        assertThat(resultado.saida()).doesNotContain("linha-1\n");
    }

    @Test
    void estouraTimeoutDeVerdadeQuandoProcessoNaoTermina() {
        CommandResult resultado = assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                runner.run(List.of("sleep", "10"), Path.of("."), Duration.ofSeconds(1)));

        assertThat(resultado.codigoDeSaida()).isEqualTo(124);
    }
}
