package com.trade.triage.gateway.evidence.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "triage.observabilidade")
public record ObservabilityProperties(String lokiUrl, String prometheusUrl, String tempoUrl, String grafanaUrl,
                                      Duration timeout) {

    public ObservabilityProperties {
        lokiUrl = lokiUrl == null || lokiUrl.isBlank() ? "http://localhost:3100" : lokiUrl;
        prometheusUrl = prometheusUrl == null || prometheusUrl.isBlank() ? "http://localhost:9090" : prometheusUrl;
        tempoUrl = tempoUrl == null || tempoUrl.isBlank() ? "http://localhost:3200" : tempoUrl;
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    }
}
