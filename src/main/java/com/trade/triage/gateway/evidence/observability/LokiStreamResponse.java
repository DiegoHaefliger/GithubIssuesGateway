package com.trade.triage.gateway.evidence.observability;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiStreamResponse(@JsonProperty("data") Data data) {

    public List<String> linhas() {
        if (data == null || data.result() == null) {
            return List.of();
        }
        return data.result().stream()
                .flatMap(stream -> stream.values().stream())
                .filter(valor -> valor.size() > 1)
                .map(valor -> valor.get(1))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("result") List<Stream> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stream(@JsonProperty("values") List<List<String>> values) {

        public Stream {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
