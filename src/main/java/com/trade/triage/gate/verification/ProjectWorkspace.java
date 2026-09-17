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

public class ProjectWorkspace implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectWorkspace.class);
    private static final Duration TIMEOUT_DE_GIT = Duration.ofMinutes(5);

    private final Path raiz;
    private final CommandRunner runner;

    private ProjectWorkspace(Path raiz, CommandRunner runner) {
        this.raiz = raiz;
        this.runner = runner;
    }

    public static ProjectWorkspace clonar(ProjectEntry projeto, Path diretorioBase, CommandRunner runner) {
        try {
            Files.createDirectories(diretorioBase);
            Path destino = Files.createTempDirectory(diretorioBase, projeto.projeto() + "-");
            CommandResult clone = runner.run(
                    List.of("git", "clone", "--local", "--no-hardlinks", "--branch", projeto.branchBase(),
                            projeto.diretorio(), destino.toString()),
                    diretorioBase, TIMEOUT_DE_GIT);
            if (!clone.sucesso()) {
                throw new VerificationException("Falha ao clonar " + projeto.diretorio() + ": " + clone.saida());
            }
            return new ProjectWorkspace(destino, runner);
        } catch (IOException exception) {
            throw new VerificationException("Falha ao preparar worktree de " + projeto.projeto(), exception);
        }
    }

    public Path raiz() {
        return raiz;
    }

    public boolean aplicar(String diff) {
        try {
            Path arquivo = Files.createTempFile(raiz, "patch-", ".diff");
            Files.writeString(arquivo, diff, StandardCharsets.UTF_8);
            CommandResult resultado = runner.run(
                    List.of("git", "apply", "--whitespace=nowarn", arquivo.toString()), raiz, TIMEOUT_DE_GIT);
            Files.deleteIfExists(arquivo);
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
