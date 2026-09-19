package com.trade.triage.gateway.evidence.observability;

import com.trade.triage.gateway.evidence.OccurrenceSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LokiPrometheusClient implements ObservabilityClient {

    private static final Logger LOG = LoggerFactory.getLogger(LokiPrometheusClient.class);
    private static final int LIMITE_DE_LINHAS = 200;
    private static final String PASSO_SERIE = "300s";
    private static final String CABECALHO_CODIFICACAO = "X-Loki-Response-Encoding-Flags";
    private static final String CATEGORIZAR_LABELS = "categorize-labels";

    private final RestClient loki;
    private final RestClient prometheus;

    public LokiPrometheusClient(RestClient lokiRestClient, RestClient prometheusRestClient) {
        this.loki = lokiRestClient;
        this.prometheus = prometheusRestClient;
    }

    @Override
    public List<String> logsPorTrace(String traceId, Instant inicio, Instant fim) {
        // trace_id e' structured metadata, nao label; sem "| json" (log ja chega estruturado via OTLP)
        return consultarLogs("{service_name=~\".+\"} | trace_id=\"" + traceId + "\"", inicio, fim);
    }

    @Override
    public List<String> logsPorServico(String service, String env, Instant inicio, Instant fim) {
        // service_name e' o label indexado; env e' structured metadata, filtra com "|"
        return consultarLogs("{service_name=\"" + service + "\"} | env=\"" + env
                + "\" | severity_text=~\"ERROR|FATAL\"", inicio, fim);
    }

    @Override
    public List<OccurrenceSample> serieDeErros(String service, String env, Instant inicio, Instant fim) {
        String consulta = "sum(count_over_time({service_name=\"" + service + "\"} | env=\"" + env
                + "\" | severity_text=\"ERROR\" [5m]))";
        try {
            LokiMatrixResponse resposta = loki.get()
                    .uri(builder -> builder.path("/loki/api/v1/query_range")
                            .queryParam("query", "{query}")
                            .queryParam("start", inicio.toEpochMilli() * 1_000_000L)
                            .queryParam("end", fim.toEpochMilli() * 1_000_000L)
                            .queryParam("step", PASSO_SERIE)
                            .build(consulta))
                    .retrieve()
                    .body(LokiMatrixResponse.class);
            return resposta == null ? List.of() : resposta.amostras();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar serie de erros service={} motivo={}", service, exception.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, String> metricasDoServico(String service, Instant inicio, Instant fim) {
        Map<String, String> consultas = new LinkedHashMap<>();
        consultas.put("latencia_p95",
                "histogram_quantile(0.95, sum(rate(http_server_request_duration_seconds_bucket{job=\""
                        + service + "\"}[5m])) by (le))");
        consultas.put("taxa_de_erro",
                "sum(rate(http_server_request_duration_seconds_count{job=\"" + service
                        + "\", http_response_status_code=~\"5..\"}[5m]))");
        consultas.put("heap_usada", "sum(jvm_memory_used_bytes{job=\"" + service + "\", jvm_memory_type=\"heap\"})");

        Map<String, String> resultado = new LinkedHashMap<>();
        consultas.forEach((nome, consulta) -> resultado.put(nome, valorInstantaneo(consulta, fim)));
        return Map.copyOf(resultado);
    }

    private String valorInstantaneo(String consulta, Instant momento) {
        try {
            PrometheusVectorResponse resposta = prometheus.get()
                    .uri(builder -> builder.path("/api/v1/query")
                            .queryParam("query", "{query}")
                            .queryParam("time", momento.getEpochSecond())
                            .build(consulta))
                    .retrieve()
                    .body(PrometheusVectorResponse.class);
            return resposta == null ? "indisponivel" : resposta.primeiroValor();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar metrica motivo={}", exception.getMessage());
            return "indisponivel";
        }
    }

    private List<String> consultarLogs(String consulta, Instant inicio, Instant fim) {
        try {
            // "{query}" + build(consulta): evita UriBuilder confundir "{"/"}" do LogQL com template
            LokiStreamResponse resposta = loki.get()
                    .uri(builder -> builder.path("/loki/api/v1/query_range")
                            .queryParam("query", "{query}")
                            .queryParam("start", inicio.toEpochMilli() * 1_000_000L)
                            .queryParam("end", fim.toEpochMilli() * 1_000_000L)
                            .queryParam("limit", LIMITE_DE_LINHAS)
                            .build(consulta))
                    .header(CABECALHO_CODIFICACAO, CATEGORIZAR_LABELS)
                    .retrieve()
                    .body(LokiStreamResponse.class);
            return resposta == null ? List.of() : resposta.linhas();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar logs motivo={}", exception.getMessage());
            return List.of();
        }
    }
}
