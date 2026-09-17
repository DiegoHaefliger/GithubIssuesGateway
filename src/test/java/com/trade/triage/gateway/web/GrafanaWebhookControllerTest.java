package com.trade.triage.gateway.web;

import com.trade.triage.gateway.service.AlertIngestService;
import com.trade.triage.gateway.service.IngestOutcome;
import com.trade.triage.gateway.service.IngestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GrafanaWebhookController.class)
class GrafanaWebhookControllerTest {

    private static final String PAYLOAD = """
            {
              "status": "firing",
              "alerts": [
                {
                  "status": "firing",
                  "labels": {"service": "trade-backend", "env": "producao", "severity": "critical"},
                  "annotations": {"exception_class": "java.lang.NullPointerException", "message": "boom"},
                  "startsAt": "2026-09-17T12:00:00Z"
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertIngestService ingestService;

    @Test
    void respondeAceitoComOResultadoDaIngestao() throws Exception {
        when(ingestService.ingest(any())).thenReturn(
                List.of(IngestResult.comCard(IngestOutcome.CARD_CRIADO, "a3f9c2d1", "acme/trade#123")));

        mockMvc.perform(post("/webhooks/grafana").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recebidos").value(1))
                .andExpect(jsonPath("$.resultados[0].resultado").value("CARD_CRIADO"))
                .andExpect(jsonPath("$.resultados[0].card").value("acme/trade#123"));

        verify(ingestService).ingest(any());
    }

    @Test
    void rejeitaPayloadSemAlertas() throws Exception {
        mockMvc.perform(post("/webhooks/grafana")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"firing\", \"alerts\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejeitaCorpoMalformado() throws Exception {
        mockMvc.perform(post("/webhooks/grafana").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }
}
