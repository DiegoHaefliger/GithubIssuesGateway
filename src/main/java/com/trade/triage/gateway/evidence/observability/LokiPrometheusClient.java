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

    private final RestClient loki;
    private final RestClient prometheus;

    public LokiPrometheusClient(RestClient lokiRestClient, RestClient prometheusRestClient) {
        this.loki = lokiRestClient;
        this.prometheus = prometheusRestClient;
    }

    @Override
    public List<String> logsPorTrace(String traceId, Instant inicio, Instant fim) {
        return consultarLogs("{env=~\".+\"} | json | trace_id=\"" + traceId + "\"", inicio, fim);
    }

    @Override
    public List<String> logsPorServico(String service, String env, Instant inicio, Instant fim) {
        return consultarLogs("{service=\"" + service + "\", env=\"" + env + "\"}", inicio, fim);
    }

    @Override
    public List<OccurrenceSample> serieDeErros(String service, String env, Instant inicio, Instant fim) {
        String consulta = "sum(count_over_time({service=\"" + service + "\", env=\"" + env
                + "\", level=\"ERROR\"}[5m]))";
        try {
            LokiMatrixResponse resposta = loki.get()
                    .uri(builder -> builder.path("/loki/api/v1/query_range")
                            .queryParam("query", consulta)
                            .queryParam("start", inicio.toEpochMilli() * 1_000_000L)
                            .queryParam("end", fim.toEpochMilli() * 1_000_000L)
                            .queryParam("step", PASSO_SERIE)
                            .build())
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
                "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{service=\""
                        + service + "\"}[5m])) by (le))");
        consultas.put("taxa_de_erro",
                "sum(rate(http_server_requests_seconds_count{service=\"" + service + "\", outcome=\"SERVER_ERROR\"}[5m]))");
        consultas.put("heap_usada", "sum(jvm_memory_used_bytes{service=\"" + service + "\", area=\"heap\"})");

        Map<String, String> resultado = new LinkedHashMap<>();
        consultas.forEach((nome, consulta) -> resultado.put(nome, valorInstantaneo(consulta, fim)));
        return Map.copyOf(resultado);
    }

    private String valorInstantaneo(String consulta, Instant momento) {
        try {
            PrometheusVectorResponse resposta = prometheus.get()
                    .uri(builder -> builder.path("/api/v1/query")
                            .queryParam("query", consulta)
                            .queryParam("time", momento.getEpochSecond())
                            .build())
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
            LokiStreamResponse resposta = loki.get()
                    .uri(builder -> builder.path("/loki/api/v1/query_range")
                            .queryParam("query", consulta)
                            .queryParam("start", inicio.toEpochMilli() * 1_000_000L)
                            .queryParam("end", fim.toEpochMilli() * 1_000_000L)
                            .queryParam("limit", LIMITE_DE_LINHAS)
                            .build())
                    .retrieve()
                    .body(LokiStreamResponse.class);
            return resposta == null ? List.of() : resposta.linhas();
        } catch (RuntimeException exception) {
            LOG.warn("falha ao consultar logs motivo={}", exception.getMessage());
            return List.of();
        }
    }
}
