package com.trade.triage.orchestrator.publish;

import com.trade.triage.board.github.GitHubProperties;
import com.trade.triage.gate.GateDecision;
import com.trade.triage.gate.verification.CommandResult;
import com.trade.triage.gate.verification.CommandRunner;
import com.trade.triage.gate.verification.ProjectWorkspace;
import com.trade.triage.gate.verification.VerificationException;
import com.trade.triage.gate.verification.VerificationProperties;
import com.trade.triage.gateway.scrub.SecretScrubber;
import com.trade.triage.orchestrator.result.TriageResult;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
public class GitPatchPublisher implements PatchPublisher {

    private static final Duration TIMEOUT_DE_GIT = Duration.ofMinutes(5);
    private static final String PREFIXO_DA_BRANCH = "triagem/";

    private final CommandRunner runner;
    private final VerificationProperties properties;
    private final GitHubProperties githubProperties;
    private final CommitMessageBuilder commitMessageBuilder;
    private final SecretScrubber scrubber;

    public GitPatchPublisher(CommandRunner runner,
                             VerificationProperties properties,
                             GitHubProperties githubProperties,
                             CommitMessageBuilder commitMessageBuilder,
                             SecretScrubber scrubber) {
        this.runner = runner;
        this.properties = properties;
        this.githubProperties = githubProperties;
        this.commitMessageBuilder = commitMessageBuilder;
        this.scrubber = scrubber;
    }

    @Override
    public String publicarBranch(TriageJobEntity job, ProjectEntry projeto, TriageResult resultado,
                                 GateDecision decisao, BlastRadius blastRadius) {
        String branch = PREFIXO_DA_BRANCH + job.getFingerprint();
        try (ProjectWorkspace worktree = ProjectWorkspace.clonar(
                projeto, Path.of(properties.diretorioDeTrabalho()), runner)) {

            executar(worktree, List.of("git", "checkout", "-b", branch), "criar branch");
            if (!worktree.aplicar(resultado.diff())) {
                throw new VerificationException("Patch aprovado pelo gate nao aplica na branch " + branch);
            }
            executar(worktree, List.of("git", "add", "--all"), "preparar arquivos");
            executar(worktree, List.of("git", "commit", "-m",
                    commitMessageBuilder.build(job, resultado, decisao, blastRadius)), "commitar");
            executar(worktree, List.of("git", "push", urlComCredencial(projeto), branch, "--force-with-lease"),
                    "publicar branch no repositorio remoto");
            return branch;
        }
    }

    private String urlComCredencial(ProjectEntry projeto) {
        return "https://x-access-token:%s@github.com/%s.git"
                .formatted(githubProperties.token(), projeto.repositorio());
    }

    private String limpar(String saida) {
        String token = githubProperties.token();
        String semToken = token == null || token.isBlank()
                ? saida
                : saida.replace(token, SecretScrubber.REDIGIDO);
        return scrubber.scrubText(semToken);
    }

    private void executar(ProjectWorkspace worktree, List<String> comando, String descricao) {
        CommandResult resultado = runner.run(comando, worktree.raiz(), TIMEOUT_DE_GIT);
        if (!resultado.sucesso()) {
            throw new VerificationException("Falha ao " + descricao + ": " + limpar(resultado.saida()));
        }
    }
}
