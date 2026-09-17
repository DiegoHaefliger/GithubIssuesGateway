package com.trade.triage.gateway.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GrafanaAlert(
        @JsonProperty("status") String status,
        @JsonProperty("labels") Map<String, String> labels,
        @JsonProperty("annotations") Map<String, String> annotations,
        @JsonProperty("startsAt") Instant startsAt,
        @JsonProperty("generatorURL") String generatorUrl) {

    public GrafanaAlert {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }

    public String label(String chave) {
        return labels.get(chave);
    }

    public String annotation(String chave) {
        return annotations.get(chave);
    }

    public boolean firing() {
        return "firing".equalsIgnoreCase(status);
    }
}
