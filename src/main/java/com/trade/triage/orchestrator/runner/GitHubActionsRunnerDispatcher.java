package com.trade.triage.orchestrator.runner;

import com.trade.triage.board.github.BoardOperationException;
import com.trade.triage.board.github.GitHubProperties;
import com.trade.triage.orchestrator.job.OrchestratorProperties;
import com.trade.triage.persistence.entity.TriageJobEntity;
import com.trade.triage.registry.model.ProjectEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class GitHubActionsRunnerDispatcher implements RunnerDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubActionsRunnerDispatcher.class);

    private final RestClient restClient;
    private final GitHubProperties githubProperties;
    private final OrchestratorProperties orchestratorProperties;

    public GitHubActionsRunnerDispatcher(RestClient githubRestClient,
                                         GitHubProperties githubProperties,
                                         OrchestratorProperties orchestratorProperties) {
        this.restClient = githubRestClient;
        this.githubProperties = githubProperties;
        this.orchestratorProperties = orchestratorProperties;
    }

    @Override
    public String dispatch(TriageJobEntity job, ProjectEntry projeto) {
        String[] partes = projeto.repositorio().split("/");
        if (partes.length != 2) {
            throw new BoardOperationException("Repositorio fora do formato owner/repo: " + projeto.repositorio());
        }
        restClient.post()
                .uri("/repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches",
                        partes[0], partes[1], githubProperties.workflowArquivo())
                .body(Map.of("ref", projeto.branchBase(), "inputs", Map.of(
                        "job_id", job.getJobId(),
                        "card", job.getCardRef(),
                        "fingerprint", job.getFingerprint(),
                        "callback_url", orchestratorProperties.urlPublica())))
                .retrieve()
                .toBodilessEntity();

        LOG.info("runner disparado job={} repositorio={} workflow={}",
                job.getJobId(), projeto.repositorio(), githubProperties.workflowArquivo());
        return projeto.repositorio() + ":" + githubProperties.workflowArquivo();
    }
}
