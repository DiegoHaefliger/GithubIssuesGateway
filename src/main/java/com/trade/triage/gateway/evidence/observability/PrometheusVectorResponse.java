package com.trade.triage.gateway.evidence.observability;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusVectorResponse(@JsonProperty("data") Data data) {

    public String primeiroValor() {
        if (data == null || data.result() == null || data.result().isEmpty()) {
            return "indisponivel";
        }
        List<Object> valor = data.result().getFirst().value();
        return valor.size() > 1 ? String.valueOf(valor.get(1)) : "indisponivel";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("result") List<Amostra> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amostra(@JsonProperty("value") List<Object> value) {

        public Amostra {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }
}
