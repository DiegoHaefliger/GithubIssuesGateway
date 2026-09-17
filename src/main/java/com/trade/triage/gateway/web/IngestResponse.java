package com.trade.triage.gateway.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trade.triage.gateway.service.IngestResult;

import java.util.List;

public record IngestResponse(
        @JsonProperty("recebidos") int recebidos,
        @JsonProperty("resultados") List<IngestResult> resultados) {

    public IngestResponse {
        resultados = resultados == null ? List.of() : List.copyOf(resultados);
    }
}
