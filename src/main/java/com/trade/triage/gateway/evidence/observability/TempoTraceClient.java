package com.trade.triage.gateway.evidence.observability;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class TempoTraceClient implements TraceClient {

    private static final Logger LOG = LoggerFactory.getLogger(TempoTraceClient.class);
    private static final long NANOS_POR_MILI = 1_000_000L;
    private static final Set<String> ATRIBUTOS_RELEVANTES = Set.of(
            "http.request.method", "http.route", "url.path", "url.query", "http.response.status_code",
            "error.type", "code.function.name", "db.system", "db.operation.name", "db.query.text",
            "server.address");

    private final RestClient tempo;

    public TempoTraceClient(RestClient tempoRestClient) {
        this.tempo = tempoRestClient;
    }

    @Override
    public List<String> spansDoTrace(String traceId) {
        try {
            JsonNode trace = tempo.get()
                    .uri("/api/traces/{traceId}", traceId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            return trace == null ? List.of() : resumir(trace);
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar trace no Tempo motivo={}", exception.getMessage());
            return List.of();
        }
    }

    private List<String> resumir(JsonNode trace) {
        List<String> spans = new ArrayList<>();
        trace.path("batches").forEach(lote -> lote.path("scopeSpans").forEach(escopo ->
                escopo.path("spans").forEach(span -> spans.add(resumirSpan(span)))));
        return List.copyOf(spans);
    }

    private String resumirSpan(JsonNode span) {
        String atributos = StreamSupport.stream(span.path("attributes").spliterator(), false)
                .filter(atributo -> ATRIBUTOS_RELEVANTES.contains(atributo.path("key").asText()))
                .map(atributo -> atributo.path("key").asText() + "=" + valorDe(atributo.path("value")))
                .collect(Collectors.joining(" "));
        long duracaoMs = (span.path("endTimeUnixNano").asLong() - span.path("startTimeUnixNano").asLong())
                / NANOS_POR_MILI;
        String status = span.path("status").path("code").asText("");
        return "%s | %s | status=%s | %dms".formatted(span.path("name").asText("?"), atributos,
                status.isBlank() ? "-" : status, duracaoMs);
    }

    private String valorDe(JsonNode valor) {
        return valor.fieldNames().hasNext() ? valor.path(valor.fieldNames().next()).asText() : "";
    }
}
