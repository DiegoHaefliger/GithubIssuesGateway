package com.trade.triage.gateway.evidence.observability;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiStreamResponse(@JsonProperty("data") Data data) {

    private static final String STRUCTURED_METADATA = "structuredMetadata";
    private static final List<String> CAMPOS_DA_EXCECAO =
            List.of("exception_type", "exception_message", "code_function_name", "exception_stacktrace");

    public List<String> linhas() {
        if (data == null || data.result() == null) {
            return List.of();
        }
        return data.result().stream()
                .flatMap(stream -> stream.values().stream())
                .filter(valor -> valor.size() > 1)
                .map(LokiStreamResponse::linhaCom)
                .toList();
    }

    private static String linhaCom(List<Object> valor) {
        String corpo = String.valueOf(valor.get(1));
        if (valor.size() < 3 || !(valor.get(2) instanceof Map<?, ?> categorias)) {
            return corpo;
        }
        if (!(categorias.get(STRUCTURED_METADATA) instanceof Map<?, ?> metadados)) {
            return corpo;
        }
        String excecao = CAMPOS_DA_EXCECAO.stream()
                .filter(campo -> metadados.get(campo) != null)
                .map(campo -> campo + "=" + metadados.get(campo))
                .collect(Collectors.joining("\n"));
        return Stream.of(corpo, excecao).filter(parte -> !parte.isBlank()).collect(Collectors.joining("\n"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("result") List<StreamResult> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamResult(@JsonProperty("values") List<List<Object>> values) {

        public StreamResult {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
