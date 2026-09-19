package com.trade.triage.gate.verification;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
            CompletableFuture<String> saidaFuture = CompletableFuture.supplyAsync(() -> lerSaida(processo));
            boolean terminou = processo.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!terminou) {
                processo.destroyForcibly();
            }
            String saida = saidaFuture.join();
            return terminou
                    ? new CommandResult(processo.exitValue(), saida)
                    : new CommandResult(CODIGO_DE_TIMEOUT, saida);
        } catch (IOException exception) {
            return new CommandResult(-1, "falha ao executar " + comando + ": " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "execucao interrompida");
        }
    }

    /** Guarda as ultimas linhas, nao as primeiras — erro de build aparece no fim da saida. */
    private String lerSaida(Process processo) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
            Deque<String> ultimasLinhas = new ArrayDeque<>();
            String linha;
            while ((linha = reader.readLine()) != null) {
                ultimasLinhas.addLast(linha);
                if (ultimasLinhas.size() > LINHAS_DE_SAIDA_GUARDADAS) {
                    ultimasLinhas.removeFirst();
                }
            }
            return String.join("\n", ultimasLinhas);
        } catch (IOException exception) {
            return "falha ao ler saida: " + exception.getMessage();
        }
    }
}
