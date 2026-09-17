package com.trade.triage.orchestrator.runner;

import com.trade.triage.persistence.entity.TriageJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubActionsRunnerCanceller implements RunnerCanceller {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubActionsRunnerCanceller.class);

    private final RestClient restClient;

    public GitHubActionsRunnerCanceller(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    @Override
    public boolean cancelar(TriageJobEntity job) {
        if (job.getRunnerRunId() == null) {
            LOG.warn("job sem execucao conhecida do runner job={} runner={}",
                    job.getJobId(), job.getRunnerRef());
            return false;
        }
        String[] partes = job.getRepositorio().split("/");
        if (partes.length != 2) {
            return false;
        }
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/actions/runs/{runId}/cancel",
                            partes[0], partes[1], job.getRunnerRunId())
                    .retrieve()
                    .toBodilessEntity();
            LOG.info("execucao do runner cancelada job={} run={}", job.getJobId(), job.getRunnerRunId());
            return true;
        } catch (RuntimeException exception) {
            LOG.error("falha ao cancelar execucao do runner job={} run={} motivo={}",
                    job.getJobId(), job.getRunnerRunId(), exception.getMessage());
            return false;
        }
    }
}
