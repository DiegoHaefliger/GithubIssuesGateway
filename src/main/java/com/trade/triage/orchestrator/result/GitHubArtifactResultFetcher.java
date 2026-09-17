package com.trade.triage.orchestrator.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trade.triage.persistence.entity.TriageJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class GitHubArtifactResultFetcher implements ResultFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubArtifactResultFetcher.class);
    private static final String ARQUIVO_DO_RESULTADO = "resultado.json";

    private final RestClient restClient;

    public GitHubArtifactResultFetcher(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    @Override
    public Optional<String> fetchRawJson(TriageJobEntity job) {
        String[] partes = job.getRepositorio().split("/");
        if (partes.length != 2) {
            return Optional.empty();
        }
        return localizarArtefato(partes[0], partes[1], job.getJobId())
                .flatMap(artefato -> baixar(partes[0], partes[1], artefato.id()));
    }

    private Optional<Artifact> localizarArtefato(String owner, String repo, String jobId) {
        ArtifactListResponse resposta = restClient.get()
                .uri(builder -> builder.path("/repos/{owner}/{repo}/actions/artifacts")
                        .queryParam("name", nomeDoArtefato(jobId))
                        .build(owner, repo))
                .retrieve()
                .body(ArtifactListResponse.class);
        if (resposta == null || resposta.artifacts().isEmpty()) {
            LOG.warn("artefato de resultado nao encontrado job={} repositorio={}/{}", jobId, owner, repo);
            return Optional.empty();
        }
        return Optional.of(resposta.artifacts().getFirst());
    }

    private Optional<String> baixar(String owner, String repo, long artefatoId) {
        byte[] zip = restClient.get()
                .uri("/repos/{owner}/{repo}/actions/artifacts/{id}/zip", owner, repo, artefatoId)
                .retrieve()
                .body(byte[].class);
        return zip == null ? Optional.empty() : extrair(zip);
    }

    private Optional<String> extrair(byte[] zip) {
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entrada;
            while ((entrada = stream.getNextEntry()) != null) {
                if (entrada.getName().endsWith(ARQUIVO_DO_RESULTADO)) {
                    return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return Optional.empty();
        } catch (IOException exception) {
            LOG.warn("artefato de resultado ilegivel motivo={}", exception.getMessage());
            return Optional.empty();
        }
    }

    public static String nomeDoArtefato(String jobId) {
        return "resultado-" + jobId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArtifactListResponse(@JsonProperty("artifacts") List<Artifact> artifacts) {

        private ArtifactListResponse {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Artifact(@JsonProperty("id") long id, @JsonProperty("name") String name) {
    }
}
