package com.trade.triage.orchestrator.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trade.triage.persistence.entity.TriageJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

@Component
public class GitHubActionsRunnerUsage implements RunnerUsage {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubActionsRunnerUsage.class);

    private final RestClient restClient;

    public GitHubActionsRunnerUsage(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    @Override
    public Optional<Duration> tempoFaturavel(TriageJobEntity job) {
        if (job.getRunnerRunId() == null) {
            return Optional.empty();
        }
        String[] partes = job.getRepositorio().split("/");
        if (partes.length != 2) {
            return Optional.empty();
        }
        try {
            TimingResponse resposta = restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{runId}/timing",
                            partes[0], partes[1], job.getRunnerRunId())
                    .retrieve()
                    .body(TimingResponse.class);
            return resposta == null ? Optional.empty() : resposta.duracao();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar consumo do runner job={} motivo={}",
                    job.getJobId(), exception.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TimingResponse(@JsonProperty("run_duration_ms") Long runDurationMs) {

        private Optional<Duration> duracao() {
            return runDurationMs == null ? Optional.empty() : Optional.of(Duration.ofMillis(runDurationMs));
        }
    }
}
