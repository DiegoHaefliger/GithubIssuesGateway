package com.trade.triage.orchestrator.web;

import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.orchestrator.job.JobEnqueueResult;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardWebhookController.class)
class BoardWebhookControllerTest {

    private static final String LABELED = """
            {
              "action": "labeled",
              "label": {"name": "auto-triage"},
              "issue": {"number": 123},
              "repository": {"full_name": "acme/trade"}
            }
            """;

    private static final String COMENTARIO = """
            {
              "action": "created",
              "issue": {"number": 123},
              "repository": {"full_name": "acme/trade"},
              "sender": {"type": "User"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSignatureVerifier verifier;

    @MockitoBean
    private TriageJobService jobService;

    @Test
    void labelAutoTriageEnfileiraJob() throws Exception {
        when(jobService.enfileirar("acme/trade#123", "issues.labeled"))
                .thenReturn(JobEnqueueResult.enfileirado("job-1"));

        mockMvc.perform(webhook("issues", LABELED))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resultado").value("ENFILEIRADO"))
                .andExpect(jsonPath("$.job").value("job-1"));
    }

    @Test
    void outraLabelNaoEnfileira() throws Exception {
        mockMvc.perform(webhook("issues", LABELED.replace("auto-triage", "bug")))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void comentarioDeHumanoRetomaATriagem() throws Exception {
        when(jobService.enfileirar("acme/trade#123", "issue_comment.created"))
                .thenReturn(JobEnqueueResult.enfileirado("job-2"));

        mockMvc.perform(webhook("issue_comment", COMENTARIO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job").value("job-2"));
    }

    @Test
    void comentarioDoProprioBotNaoRetoma() throws Exception {
        mockMvc.perform(webhook("issue_comment", COMENTARIO.replace("\"User\"", "\"Bot\"")))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void assinaturaInvalidaEhRecusada() throws Exception {
        doThrow(new UnauthorizedException("Assinatura do webhook nao confere"))
                .when(verifier).verify(anyString(), any());

        mockMvc.perform(webhook("issues", LABELED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void payloadSemRepositorioEhRecusado() throws Exception {
        mockMvc.perform(webhook("issues", LABELED.replace("\"acme/trade\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void eventoDesconhecidoEhIgnorado() throws Exception {
        mockMvc.perform(webhook("push", "{}"))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(
            String evento, String payload) {
        return post("/webhooks/board")
                .header("X-GitHub-Event", evento)
                .header("X-Hub-Signature-256", "sha256=qualquer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }
}
