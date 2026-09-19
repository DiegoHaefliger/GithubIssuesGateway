package com.trade.triage.gateway.evidence.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TempoTraceClientTest {

    private MockRestServiceServer tempoServer;
    private TempoTraceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://tempo.exemplo");
        tempoServer = MockRestServiceServer.bindTo(builder).build();
        client = new TempoTraceClient(builder.build());
    }

    @Test
    void resumeSpanComAtributosRelevantesStatusEDuracaoSemDadoDoCliente() {
        tempoServer.expect(requestTo("https://tempo.exemplo/api/traces/abc"))
                .andRespond(withSuccess("""
                        {"batches": [{"scopeSpans": [{"spans": [{
                          "name": "GET /notifications",
                          "startTimeUnixNano": "1000000000", "endTimeUnixNano": "1012000000",
                          "status": {"code": "STATUS_CODE_ERROR"},
                          "attributes": [
                            {"key": "http.route", "value": {"stringValue": "/notifications"}},
                            {"key": "http.response.status_code", "value": {"intValue": "500"}},
                            {"key": "client.address", "value": {"stringValue": "10.0.0.1"}},
                            {"key": "user_agent.original", "value": {"stringValue": "Mozilla"}}]}]}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.spansDoTrace("abc")).singleElement().satisfies(span -> assertThat(span)
                .startsWith("GET /notifications")
                .contains("http.route=/notifications")
                .contains("http.response.status_code=500")
                .contains("status=STATUS_CODE_ERROR")
                .contains("12ms")
                .doesNotContain("10.0.0.1")
                .doesNotContain("Mozilla"));
    }

    @Test
    void falhaDoTempoDevolveListaVazia() {
        tempoServer.expect(requestTo("https://tempo.exemplo/api/traces/abc")).andRespond(withServerError());

        assertThat(client.spansDoTrace("abc")).isEmpty();
    }
}
