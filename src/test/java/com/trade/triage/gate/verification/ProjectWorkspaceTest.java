package com.trade.triage.gate.verification;

import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkspaceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PATCH_NOVO_ARQUIVO = """
            diff --git a/Novo.java b/Novo.java
            new file mode 100644
            --- /dev/null
            +++ b/Novo.java
            @@ -0,0 +1 @@
            +x
            """;

    private static final String ORIGEM = "https://x-access-token:ghp_segredo@github.com/acme/trade.git";

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master",
            List.of("mvn", "test"), List.of("producao"), "acme/trade", List.of(), Map.of(),
            ProjectLimits.conservador(), true);

    @Mock
    private CommandRunner runner;

    @TempDir
    private Path base;

    @Test
    void clonaRasoDaOrigemRecebidaENuncaComFlagsDeClonagemLocal() {
        when(runner.run(any(), eq(base), any())).thenReturn(new CommandResult(0, "ok"));

        try (ProjectWorkspace worktree = ProjectWorkspace.clonar(projeto, ORIGEM, base, runner)) {
            assertThat(worktree.raiz()).isNotNull();
        }

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(runner).run(captor.capture(), eq(base), any());
        List<String> comando = captor.getValue();
        assertThat(comando).contains("--depth", "1", "--branch", "master", ORIGEM);
        assertThat(comando).doesNotContain("--local", "--no-hardlinks");
    }

    @Test
    void diretorioBaseRelativoClonaEAplicaPatchDentroDaWorktree(@TempDir Path origem) throws Exception {
        CommandRunner git = new CommandRunner();
        git.run(List.of("git", "init", "-q", "-b", "master"), origem, TIMEOUT);
        Files.writeString(origem.resolve("README.md"), "a\n");
        git.run(List.of("git", "add", "."), origem, TIMEOUT);
        git.run(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "init"),
                origem, TIMEOUT);
        Path relativo = Path.of("target", "worktrees-teste-" + UUID.randomUUID());

        try (ProjectWorkspace worktree = ProjectWorkspace.clonar(
                projeto, origem.toUri().toString(), relativo, git)) {
            assertThat(worktree.raiz().resolve("README.md")).exists();
            assertThat(worktree.aplicar(PATCH_NOVO_ARQUIVO)).isTrue();
            assertThat(worktree.raiz().resolve("Novo.java")).hasContent("x");
        }
    }

    @Test
    void erroDeCloneNaoVazaOTokenNaMensagemDaExcecao() {
        when(runner.run(any(), eq(base), any())).thenReturn(new CommandResult(128,
                "fatal: unable to access '" + ORIGEM + "/': The requested URL returned error: 403"));

        assertThatThrownBy(() -> ProjectWorkspace.clonar(projeto, ORIGEM, base, runner))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("acme/trade")
                .hasMessageNotContaining("ghp_segredo");
    }
}
