package com.trade.triage.gate.verification;

import com.trade.triage.registry.model.ProjectEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class ProjectWorkspace implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectWorkspace.class);
    private static final Duration TIMEOUT_DE_GIT = Duration.ofMinutes(5);
    // O git ecoa a URL da remote (com credencial) na propria mensagem de erro
    // de rede/auth — mesmo formato que GitHubCloneUrls gera.
    private static final Pattern URL_COM_CREDENCIAL = Pattern.compile("https://x-access-token:[^@\\s]+@");

    private final Path raiz;
    private final CommandRunner runner;

    private ProjectWorkspace(Path raiz, CommandRunner runner) {
        this.raiz = raiz;
        this.runner = runner;
    }

    /**
     * {@code origemDeClone} e' a URL remota (autenticada, ver {@code GitHubCloneUrls}), nunca um
     * caminho local: o gateway roda em deployable proprio, sem acesso ao disco de quem hospeda os
     * repositorios monitorados. Raso (--depth 1) porque so' o HEAD da branch base interessa —
     * nem verificacao nem publicacao de patch precisam de historico.
     */
    public static ProjectWorkspace clonar(
            ProjectEntry projeto, String origemDeClone, Path diretorioBase, CommandRunner runner) {
        try {
            Files.createDirectories(diretorioBase);
            Path destino = Files.createTempDirectory(diretorioBase, projeto.projeto() + "-");
            CommandResult clone = runner.run(
                    List.of("git", "clone", "--depth", "1", "--branch", projeto.branchBase(),
                            origemDeClone, destino.toString()),
                    diretorioBase, TIMEOUT_DE_GIT);
            if (!clone.sucesso()) {
                throw new VerificationException(
                        "Falha ao clonar " + projeto.repositorio() + ": " + semCredencial(clone.saida()));
            }
            return new ProjectWorkspace(destino, runner);
        } catch (IOException exception) {
            throw new VerificationException("Falha ao preparar worktree de " + projeto.projeto(), exception);
        }
    }

    public Path raiz() {
        return raiz;
    }

    private static String semCredencial(String texto) {
        return URL_COM_CREDENCIAL.matcher(texto).replaceAll("https://x-access-token:[REDIGIDO]@");
    }

    public boolean aplicar(String diff) {
        try {
            Path arquivo = Files.createTempFile(raiz, "patch-", ".diff");
            Files.writeString(arquivo, diff, StandardCharsets.UTF_8);
            CommandResult resultado = runner.run(
                    List.of("git", "apply", "--whitespace=nowarn", arquivo.toString()), raiz, TIMEOUT_DE_GIT);
            Files.deleteIfExists(arquivo);
            if (!resultado.sucesso()) {
                LOG.warn("git apply falhou codigoDeSaida={} saida={}", resultado.codigoDeSaida(), resultado.saida());
            }
            return resultado.sucesso();
        } catch (IOException exception) {
            throw new VerificationException("Falha ao aplicar patch no worktree", exception);
        }
    }

    public CommandResult rodarSuite(List<String> comando, Duration timeout) {
        return runner.run(comando, raiz, timeout);
    }

    @Override
    public void close() {
        try (var caminhos = Files.walk(raiz)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException exception) {
                    LOG.warn("nao removeu {} do worktree", caminho);
                }
            });
        } catch (IOException exception) {
            LOG.warn("nao removeu worktree {} motivo={}", raiz, exception.getMessage());
        }
    }
}
