package com.trade.triage.gateway.evidence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trade.triage.registry.model.ProjectEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Substitui leitura de {@code git log} local (gateway roda em deployable proprio, sem checkout dos
 * repositorios monitorados) pela API de commits do GitHub.
 */
@Component
public class GitHubCommitHistory implements CommitHistory {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubCommitHistory.class);
    private static final int PAGINA = 20;

    private final RestClient restClient;

    public GitHubCommitHistory(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    @Override
    public List<String> commitsRecentes(ProjectEntry projeto, Duration janela) {
        String[] partes = projeto.repositorio().split("/");
        if (partes.length != 2) {
            return List.of();
        }
        String desde = DateTimeFormatter.ISO_INSTANT.format(Instant.now().minus(janela));
        try {
            Commit[] commits = restClient.get()
                    .uri(builder -> builder.path("/repos/{owner}/{repo}/commits")
                            .queryParam("sha", projeto.branchBase())
                            .queryParam("since", desde)
                            .queryParam("per_page", PAGINA)
                            .build(partes[0], partes[1]))
                    .retrieve()
                    .body(Commit[].class);
            return commits == null ? List.of() : Arrays.stream(commits).map(Commit::descricao).toList();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar commits repositorio={} motivo={}",
                    projeto.repositorio(), exception.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Commit(@JsonProperty("sha") String sha, @JsonProperty("commit") Detalhe commit) {

        private String descricao() {
            String curto = sha == null || sha.length() < 7 ? String.valueOf(sha) : sha.substring(0, 7);
            String mensagem = commit == null || commit.message() == null ? "" : primeiraLinha(commit.message());
            String autor = commit == null || commit.author() == null ? "" : commit.author().name();
            return "%s %s %s".formatted(curto, autor, mensagem).trim();
        }

        private static String primeiraLinha(String mensagem) {
            int quebra = mensagem.indexOf('\n');
            return quebra < 0 ? mensagem : mensagem.substring(0, quebra);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Detalhe(@JsonProperty("message") String message, @JsonProperty("author") Autor author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Autor(@JsonProperty("name") String name) {}
}
