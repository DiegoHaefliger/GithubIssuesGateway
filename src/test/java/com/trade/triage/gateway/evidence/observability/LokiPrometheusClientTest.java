package com.trade.triage.gateway.evidence.observability;

import com.trade.triage.gateway.evidence.OccurrenceSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LokiPrometheusClientTest {

    private MockRestServiceServer lokiServer;
    private MockRestServiceServer prometheusServer;
    private LokiPrometheusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder lokiBuilder = restClientBuilderPara("https://loki.exemplo");
        RestClient.Builder prometheusBuilder = restClientBuilderPara("https://prometheus.exemplo");
        lokiServer = MockRestServiceServer.bindTo(lokiBuilder).build();
        prometheusServer = MockRestServiceServer.bindTo(prometheusBuilder).build();
        client = new LokiPrometheusClient(lokiBuilder.build(), prometheusBuilder.build());
    }

    private RestClient.Builder restClientBuilderPara(String baseUrl) {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        return RestClient.builder().baseUrl(baseUrl).uriBuilderFactory(uriBuilderFactory);
    }

    // MockRestRequestMatchers.queryParam compara o valor cru, sem decodificar
    private RequestMatcher queryParamDecodificado(String nome, String valorEsperado) {
        return request -> {
            String bruto = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(nome);
            String decodificado = bruto == null ? null : URLDecoder.decode(bruto, StandardCharsets.UTF_8);
            AssertionErrors.assertEquals("Query param [" + nome + "]", valorEsperado, decodificado);
        };
    }

    @Test
    void logsPorTraceFiltraStructuredMetadataSemJsonESemLabelEnvInexistente() {
        lokiServer.expect(requestTo(startsWith("https://loki.exemplo/loki/api/v1/query_range")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(queryParamDecodificado("query", "{service_name=~\".+\"} | trace_id=\"abc123\""))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"values": [["1000000000", "linha 1"]]}]}}
                        """, MediaType.APPLICATION_JSON));

        List<String> linhas = client.logsPorTrace("abc123", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        assertThat(linhas).containsExactly("linha 1");
        lokiServer.verify();
    }

    @Test
    void logsPorServicoUsaServiceNameComoStreamSelectorEFiltraApenasErros() {
        lokiServer.expect(requestTo(startsWith("https://loki.exemplo/loki/api/v1/query_range")))
                .andExpect(queryParamDecodificado("query",
                        "{service_name=\"crypto-alerts-java\"} | env=\"producao\" | severity_text=~\"ERROR|FATAL\""))
                .andRespond(withSuccess("""
                        {"data": {"result": []}}
                        """, MediaType.APPLICATION_JSON));

        client.logsPorServico("crypto-alerts-java", "producao", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        lokiServer.verify();
    }

    @Test
    void logsIncluemExcecaoEStacktraceDoStructuredMetadata() {
        lokiServer.expect(requestTo(startsWith("https://loki.exemplo/loki/api/v1/query_range")))
                .andExpect(header("X-Loki-Response-Encoding-Flags", "categorize-labels"))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"stream": {"service_name": "crypto-alerts-java"}, "values": [
                          ["1000000000", "HTTP Request to /notifications failed",
                           {"structuredMetadata": {
                             "exception_type": "java.lang.IllegalArgumentException",
                             "exception_message": "Could not resolve attribute 'read'",
                             "exception_stacktrace": "java.lang.IllegalArgumentException\\n\\tat X.y(X.java:1)"}}],
                          ["2000000000", "linha sem metadado", {}]]}]}}
                        """, MediaType.APPLICATION_JSON));

        List<String> linhas = client.logsPorServico(
                "crypto-alerts-java", "producao", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        assertThat(linhas).containsExactly(
                "HTTP Request to /notifications failed\n"
                        + "exception_type=java.lang.IllegalArgumentException\n"
                        + "exception_message=Could not resolve attribute 'read'\n"
                        + "exception_stacktrace=java.lang.IllegalArgumentException\n\tat X.y(X.java:1)",
                "linha sem metadado");
        lokiServer.verify();
    }

    @Test
    void ultimoErroFiltraPelaClasseDaExcecaoELeTraceStackERotaDoMetadado() {
        lokiServer.expect(requestTo(startsWith("https://loki.exemplo/loki/api/v1/query_range")))
                .andExpect(queryParamDecodificado("query",
                        "{service_name=\"crypto-alerts-java\"} | env=\"producao\" | severity_text=~\"ERROR|FATAL\""
                                + " | exception_type=\"java.lang.IllegalArgumentException\""))
                .andExpect(queryParamDecodificado("limit", "1"))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"values": [
                          ["1000000000", "HTTP Request to /notifications failed",
                           {"structuredMetadata": {"trace_id": "abc", "http_path": "/notifications",
                             "exception_message": "Could not resolve attribute 'read'",
                             "exception_stacktrace": "java.lang.IllegalArgumentException"}}]]}]}}
                        """, MediaType.APPLICATION_JSON));

        Optional<ErroRegistrado> erro = client.ultimoErro("crypto-alerts-java", "producao",
                "java.lang.IllegalArgumentException", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        assertThat(erro).contains(new ErroRegistrado("abc", "Could not resolve attribute 'read'",
                "java.lang.IllegalArgumentException", "/notifications"));
        lokiServer.verify();
    }

    @Test
    void ultimoErroSemMetadadoEstruturadoDevolveVazio() {
        lokiServer.expect(requestTo(startsWith("https://loki.exemplo/loki/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"values": [["1000000000", "linha crua"]]}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.ultimoErro("crypto-alerts-java", "producao", null,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1))).isEmpty();
    }

    @Test
    void serieDeErrosFiltraSeverityTextNaoLevel() {
        lokiServer.expect(requestTo(startsWith("https://loki.exemplo/loki/api/v1/query_range")))
                .andExpect(queryParamDecodificado("query",
                        "sum(count_over_time({service_name=\"crypto-alerts-java\"} | env=\"producao\" "
                                + "| severity_text=\"ERROR\" [5m]))"))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"values": [[1000000000, "3"]]}]}}
                        """, MediaType.APPLICATION_JSON));

        List<OccurrenceSample> serie = client.serieDeErros(
                "crypto-alerts-java", "producao", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        assertThat(serie).hasSize(1);
        lokiServer.verify();
    }

    @Test
    void metricasDoServicoUsaJobJvmMemoryTypeEMetricaCorreta() {
        prometheusServer.expect(requestTo(startsWith("https://prometheus.exemplo/api/v1/query")))
                .andExpect(queryParamDecodificado("query",
                        "histogram_quantile(0.95, sum(rate(http_server_request_duration_seconds_bucket"
                                + "{job=\"crypto-alerts-java\"}[5m])) by (le))"))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"value": [1000000000, "0.2"]}]}}
                        """, MediaType.APPLICATION_JSON));
        prometheusServer.expect(requestTo(startsWith("https://prometheus.exemplo/api/v1/query")))
                .andExpect(queryParamDecodificado("query",
                        "sum(rate(http_server_request_duration_seconds_count"
                                + "{job=\"crypto-alerts-java\", http_response_status_code=~\"5..\"}[5m]))"))
                .andRespond(withSuccess("""
                        {"data": {"result": []}}
                        """, MediaType.APPLICATION_JSON));
        prometheusServer.expect(requestTo(startsWith("https://prometheus.exemplo/api/v1/query")))
                .andExpect(queryParamDecodificado("query",
                        "sum(jvm_memory_used_bytes{job=\"crypto-alerts-java\", jvm_memory_type=\"heap\"})"))
                .andRespond(withSuccess("""
                        {"data": {"result": [{"value": [1000000000, "104857600"]}]}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, String> metricas = client.metricasDoServico(
                "crypto-alerts-java", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        assertThat(metricas)
                .containsEntry("latencia_p95", "0.2")
                .containsEntry("taxa_de_erro", "indisponivel")
                .containsEntry("heap_usada", "104857600");
        prometheusServer.verify();
    }
}
