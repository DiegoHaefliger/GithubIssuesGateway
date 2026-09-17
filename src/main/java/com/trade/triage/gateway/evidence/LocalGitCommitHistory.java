package com.trade.triage.gateway.evidence;

import com.trade.triage.registry.model.ProjectEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LocalGitCommitHistory implements CommitHistory {

    private static final Logger LOG = LoggerFactory.getLogger(LocalGitCommitHistory.class);
    private static final int MAXIMO_DE_COMMITS = 20;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public List<String> commitsRecentes(ProjectEntry projeto, Duration janela) {
        Path diretorio = Path.of(projeto.diretorio());
        if (!Files.isDirectory(diretorio.resolve(".git"))) {
            LOG.warn("diretorio do projeto sem repositorio git projeto={} diretorio={}",
                    projeto.projeto(), diretorio);
            return List.of();
        }
        try {
            return executarGitLog(diretorio, janela);
        } catch (IOException exception) {
            LOG.warn("falha ao ler historico git projeto={} motivo={}", projeto.projeto(), exception.getMessage());
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private List<String> executarGitLog(Path diretorio, Duration janela) throws IOException, InterruptedException {
        Process processo = new ProcessBuilder(
                "git", "-C", diretorio.toString(), "log",
                "--since=" + janela.toHours() + ".hours",
                "--max-count=" + MAXIMO_DE_COMMITS,
                "--pretty=format:%h %an %s")
                .redirectErrorStream(false)
                .start();
        List<String> linhas;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
            linhas = reader.lines().toList();
        }
        if (!processo.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            return List.of();
        }
        return processo.exitValue() == 0 ? linhas : List.of();
    }
}
