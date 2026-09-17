package com.trade.triage.gateway.evidence.observability;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trade.triage.gateway.evidence.OccurrenceSample;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiMatrixResponse(@JsonProperty("data") Data data) {

    public List<OccurrenceSample> amostras() {
        if (data == null || data.result() == null) {
            return List.of();
        }
        return data.result().stream()
                .flatMap(serie -> serie.values().stream())
                .filter(ponto -> ponto.size() > 1)
                .map(LokiMatrixResponse::converter)
                .toList();
    }

    private static OccurrenceSample converter(List<Object> ponto) {
        double segundos = Double.parseDouble(String.valueOf(ponto.get(0)));
        double valor = Double.parseDouble(String.valueOf(ponto.get(1)));
        return new OccurrenceSample(Instant.ofEpochMilli((long) (segundos * 1000)), valor);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("result") List<Serie> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Serie(@JsonProperty("values") List<List<Object>> values) {

        public Serie {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
