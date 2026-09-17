package com.trade.triage.gate.verification;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class CommandRunner {

    private static final int CODIGO_DE_TIMEOUT = 124;
    private static final int LINHAS_DE_SAIDA_GUARDADAS = 200;

    public CommandResult run(List<String> comando, Path diretorio, Duration timeout) {
        try {
            Process processo = new ProcessBuilder(comando)
                    .directory(diretorio.toFile())
                    .redirectErrorStream(true)
                    .start();
            String saida = lerSaida(processo);
            if (!processo.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                return new CommandResult(CODIGO_DE_TIMEOUT, saida);
            }
            return new CommandResult(processo.exitValue(), saida);
        } catch (IOException exception) {
            return new CommandResult(-1, "falha ao executar " + comando + ": " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "execucao interrompida");
        }
    }

    private String lerSaida(Process processo) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().limit(LINHAS_DE_SAIDA_GUARDADAS).collect(Collectors.joining("\n"));
        }
    }
}
