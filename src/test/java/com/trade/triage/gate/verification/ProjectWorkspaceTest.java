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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkspaceTest {

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
    void erroDeCloneNaoVazaOTokenNaMensagemDaExcecao() {
        when(runner.run(any(), eq(base), any())).thenReturn(new CommandResult(128,
                "fatal: unable to access '" + ORIGEM + "/': The requested URL returned error: 403"));

        assertThatThrownBy(() -> ProjectWorkspace.clonar(projeto, ORIGEM, base, runner))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("acme/trade")
                .hasMessageNotContaining("ghp_segredo");
    }
}
