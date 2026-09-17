package com.trade.triage.gateway.evidence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trade.triage.registry.model.ProjectEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component
public class GitHubDeploymentHistory implements DeploymentHistory {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubDeploymentHistory.class);
    private static final int PAGINA = 20;

    private final RestClient restClient;
    private final Clock clock;

    public GitHubDeploymentHistory(RestClient githubRestClient, Clock clock) {
        this.restClient = githubRestClient;
        this.clock = clock;
    }

    @Override
    public List<String> deploysRecentes(ProjectEntry projeto, String env, Duration janela) {
        String[] partes = projeto.repositorio().split("/");
        if (partes.length != 2) {
            return List.of();
        }
        Instant limite = clock.instant().minus(janela);
        try {
            Deployment[] deployments = restClient.get()
                    .uri(builder -> builder.path("/repos/{owner}/{repo}/deployments")
                            .queryParam("environment", env)
                            .queryParam("per_page", PAGINA)
                            .build(partes[0], partes[1]))
                    .retrieve()
                    .body(Deployment[].class);
            if (deployments == null) {
                return List.of();
            }
            return Arrays.stream(deployments)
                    .filter(deployment -> deployment.createdAt() != null
                            && deployment.createdAt().isAfter(limite))
                    .map(Deployment::descricao)
                    .toList();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar deploys repositorio={} motivo={}",
                    projeto.repositorio(), exception.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Deployment(
            @JsonProperty("sha") String sha,
            @JsonProperty("ref") String ref,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("description") String description) {

        private String descricao() {
            String curto = sha == null || sha.length() < 7 ? String.valueOf(sha) : sha.substring(0, 7);
            return "%s %s em %s".formatted(curto, ref, createdAt);
        }
    }
}
