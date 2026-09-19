package com.trade.triage.orchestrator.publish;

import com.trade.triage.board.github.GitHubCloneUrls;
import com.trade.triage.board.github.GitHubProperties;
import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.verification.CommandResult;
import com.trade.triage.gate.verification.CommandRunner;
import com.trade.triage.gate.verification.VerificationProperties;
import com.trade.triage.gateway.scrub.ScrubProperties;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitPatchPublisherTest {

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master",
            List.of("mvn", "test"), List.of("producao"), "acme/trade", List.of(), Map.of(),
            ProjectLimits.conservador(), true);

    @Mock
    private CommandRunner runner;

    @Mock
    private CommitMessageBuilder commitMessageBuilder;

    @Mock
    private TriageJobEntity job;

    @TempDir
    private Path base;

    @Test
    void commitLevaIdentidadeExplicitaPorqueOContainerNaoTemGitconfig() {
        when(runner.run(any(), any(), any())).thenReturn(new CommandResult(0, ""));
        when(job.getFingerprint()).thenReturn("abc123");
        when(commitMessageBuilder.build(any(), any(), any(), any())).thenReturn("fix: x");
        GitHubProperties github = new GitHubProperties(null, "t", null, null, Duration.ofSeconds(1));
        GitPatchPublisher publisher = new GitPatchPublisher(runner,
                new VerificationProperties(base.toString(), Duration.ofMinutes(1)), github, commitMessageBuilder,
                new SecretScrubber(new ScrubProperties(null, null)), new GitHubCloneUrls(github));

        publisher.publicarBranch(job, projeto, resultado(),
                new GateDecision(Decision.PROPOSE_PATCH, "clausula 9", "severidade critica", ""), BlastRadius.BAIXO);

        ArgumentCaptor<List<String>> comandos = ArgumentCaptor.forClass(List.class);
        verify(runner, atLeastOnce()).run(comandos.capture(), any(), any());
        List<String> commit = comandos.getAllValues().stream()
                .filter(comando -> comando.contains("commit"))
                .findFirst()
                .orElseThrow();
        assertThat(commit).contains("user.name=triage-gateway",
                "user.email=triage-gateway@users.noreply.github.com");
        assertThat(commit.indexOf("commit")).isGreaterThan(commit.indexOf("user.email=triage-gateway@users.noreply.github.com"));
    }

    private TriageResult resultado() {
        return new TriageResult("job-1", "acme/trade#1", "hipotese", List.of(), List.of(),
                "diff --git a/A.java b/A.java\n", null, "justificativa", null);
    }
}
