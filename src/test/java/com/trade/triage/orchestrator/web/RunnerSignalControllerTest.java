package com.trade.triage.orchestrator.web;

import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.orchestrator.job.TriageJobService;
import com.trade.triage.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunnerSignalController.class)
class RunnerSignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSignatureVerifier verifier;

    @MockitoBean
    private TriageJobService jobService;

    @Test
    void sinalDeSucessoMarcaJobPronto() throws Exception {
        mockMvc.perform(sinal("job-1", "{\"conclusao\": \"success\"}"))
                .andExpect(status().isAccepted());

        verify(jobService).marcarPronto("job-1");
    }

    @Test
    void sinalDeFalhaMarcaJobFalhou() throws Exception {
        mockMvc.perform(sinal("job-1", "{\"conclusao\": \"failure\", \"motivo\": \"runner caiu\"}"))
                .andExpect(status().isAccepted());

        verify(jobService).marcarFalha("job-1", "runner caiu");
    }

    @Test
    void sinalSemAssinaturaValidaEhRecusado() throws Exception {
        doThrow(new UnauthorizedException("Assinatura do webhook nao confere"))
                .when(verifier).verify(anyString(), any());

        mockMvc.perform(sinal("job-1", "{\"conclusao\": \"success\"}"))
                .andExpect(status().isUnauthorized());

        verify(jobService, never()).marcarPronto(anyString());
    }

    @Test
    void sinalSemConclusaoEhRecusado() throws Exception {
        mockMvc.perform(sinal("job-1", "{}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sinal(
            String jobId, String payload) {
        return post("/webhooks/runner/{jobId}", jobId)
                .header("X-Hub-Signature-256", "sha256=qualquer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }
}
