package com.trade.triage.gateway.scrub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LogLineScrubber {

    private final SecretScrubber scrubber;
    private final ObjectMapper objectMapper;

    public LogLineScrubber(SecretScrubber scrubber, ObjectMapper objectMapper) {
        this.scrubber = scrubber;
        this.objectMapper = objectMapper;
    }

    public String scrub(String linha) {
        if (linha == null || linha.isBlank()) {
            return linha;
        }
        return campos(linha)
                .map(campos -> serializar(scrubber.scrubFields(campos)))
                .orElseGet(() -> scrubber.scrubText(linha));
    }

    private java.util.Optional<Map<String, String>> campos(String linha) {
        try {
            JsonNode raiz = objectMapper.readTree(linha);
            if (!raiz.isObject()) {
                return java.util.Optional.empty();
            }
            Map<String, String> campos = new LinkedHashMap<>();
            raiz.fields().forEachRemaining(campo ->
                    campos.put(campo.getKey(), textoDe(campo.getValue())));
            return java.util.Optional.of(campos);
        } catch (JsonProcessingException exception) {
            return java.util.Optional.empty();
        }
    }

    private String textoDe(JsonNode valor) {
        return valor.isValueNode() ? valor.asText() : valor.toString();
    }

    private String serializar(Map<String, String> campos) {
        try {
            return objectMapper.writeValueAsString(campos);
        } catch (JsonProcessingException exception) {
            return SecretScrubber.REDIGIDO;
        }
    }
}
