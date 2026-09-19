package com.trade.triage.gateway.evidence.observability;

import java.util.Map;

public record ErroRegistrado(String traceId, String mensagem, String stacktrace, String rota) {

    static ErroRegistrado de(Map<String, String> metadados) {
        return new ErroRegistrado(
                metadados.get("trace_id"),
                metadados.get("exception_message"),
                metadados.get("exception_stacktrace"),
                metadados.get("http_path"));
    }
}
