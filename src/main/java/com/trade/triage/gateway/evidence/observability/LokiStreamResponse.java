package com.trade.triage.gateway.evidence.observability;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LokiStreamResponse(@JsonProperty("data") Data data) {

    private static final String STRUCTURED_METADATA = "structuredMetadata";
    private static final List<String> CAMPOS_DA_EXCECAO =
            List.of("exception_type", "exception_message", "code_function_name", "exception_stacktrace");

    public List<String> linhas() {
        return valores().map(LokiStreamResponse::linhaCom).toList();
    }

    public Optional<Map<String, String>> primeiroMetadado() {
        return valores().map(LokiStreamResponse::metadadosDe).filter(metadados -> !metadados.isEmpty()).findFirst();
    }

    private Stream<List<Object>> valores() {
        if (data == null || data.result() == null) {
            return Stream.empty();
        }
        return data.result().stream()
                .flatMap(stream -> stream.values().stream())
                .filter(valor -> valor.size() > 1);
    }

    private static String linhaCom(List<Object> valor) {
        String corpo = String.valueOf(valor.get(1));
        Map<String, String> metadados = metadadosDe(valor);
        String excecao = CAMPOS_DA_EXCECAO.stream()
                .filter(metadados::containsKey)
                .map(campo -> campo + "=" + metadados.get(campo))
                .collect(Collectors.joining("\n"));
        return Stream.of(corpo, excecao).filter(parte -> !parte.isBlank()).collect(Collectors.joining("\n"));
    }

    private static Map<String, String> metadadosDe(List<Object> valor) {
        if (valor.size() < 3 || !(valor.get(2) instanceof Map<?, ?> categorias)
                || !(categorias.get(STRUCTURED_METADATA) instanceof Map<?, ?> metadados)) {
            return Map.of();
        }
        Map<String, String> resultado = new LinkedHashMap<>();
        metadados.forEach((chave, conteudo) -> {
            if (conteudo != null) {
                resultado.put(String.valueOf(chave), String.valueOf(conteudo));
            }
        });
        return resultado;
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
