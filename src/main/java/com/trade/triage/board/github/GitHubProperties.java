package com.trade.triage.board.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "triage.github")
public record GitHubProperties(
        String apiUrl,
        String token,
        String webhookSecret,
        String workflowArquivo,
        Duration timeout) {

    public GitHubProperties {
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.github.com" : apiUrl;
        workflowArquivo = workflowArquivo == null || workflowArquivo.isBlank()
                ? "triage-runner.yml" : workflowArquivo;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
    }
}
